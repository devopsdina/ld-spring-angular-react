import { CurrencyPipe } from '@angular/common';
import { Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import type { LDEvaluationReason } from '@launchdarkly/js-client-sdk';
import { catchError, distinctUntilChanged, map, of, startWith, switchMap, tap } from 'rxjs';

import {
  BACKEND_FLAG,
  BACKENDS,
  BackendChoice,
  BackendId,
  DEFAULT_BACKEND,
  chooseBackend,
  urlOverride,
} from './backend';
import { flagDetailSignal, launchDarklyStatus } from './launchdarkly/flag-signal';
import { identifyContext } from './launchdarkly/ld-client';
import { ProductOffer, ProductService, productAttributes } from './product.service';
import { user, userAttributes } from './user';

export const PURCHASE_FLAG = 'enable-purchasing';

/** The latest product request: which backend it went to, and what came back. */
interface Load {
  backend: BackendId;
  product?: ProductOffer;
  failed?: boolean;
}

@Component({
  selector: 'app-root',
  imports: [CurrencyPipe],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {
  protected readonly user = user;
  protected readonly status = launchDarklyStatus;

  // Fallback false: if LaunchDarkly can't be reached, ordering stays off rather than on.
  protected readonly purchasing = flagDetailSignal(PURCHASE_FLAG, false);
  protected readonly purchaseReason = computed(() => describeReason(this.purchasing().reason));

  // Fallback spring-boot: if LaunchDarkly can't be reached, everyone stays on Spring Boot.
  private readonly backendFlag = flagDetailSignal<string>(BACKEND_FLAG, DEFAULT_BACKEND);
  private readonly override = urlOverride(window.location.search);
  private readonly flagChoice = computed(() => chooseBackend(this.backendFlag(), this.override));
  /** The backend products are requested from. */
  protected readonly backend = signal<BackendChoice>(this.flagChoice());

  private readonly products = inject(ProductService);
  private readonly productId = Math.floor(Math.random() * 5) + 1;
  // The product the SDK's context includes.
  private identified?: number;

  // switchMap unsubscribes from the previous request when the backend changes, which cancels it,
  // so a late answer from the old backend can't replace the new one.
  private readonly load = toSignal(
    toObservable(this.backend).pipe(
      map((backend) => backend.id),
      distinctUntilChanged(),
      switchMap((id) =>
        this.products.getProduct(this.productId, user, this.backend()).pipe(
          tap((product) => this.identify(product)),
          map((product): Load => ({ backend: id, product })),
          catchError(() => of<Load>({ backend: id, failed: true })),
          startWith<Load>({ backend: id }),
        ),
      ),
    ),
  );

  protected readonly product = computed(() => this.load()?.product);
  protected readonly failed = computed(() => this.load()?.failed ?? false);
  /** The backend that answered, or the one being asked. */
  protected readonly shown = computed(() => {
    const id = this.product()?.backend.id ?? this.load()?.backend ?? this.backend().id;
    return { id, ...BACKENDS[id] };
  });

  constructor() {
    // Routing only follows a successful evaluation. The SDK briefly reports ERROR while identify()
    // loads the new context's flags and while it reconnects; if that moved traffic, every product
    // load would bounce to Spring Boot and back. An error keeps the current backend instead
    // (Spring Boot to begin with).
    effect(() => {
      const next = this.flagChoice();
      const current = untracked(this.backend);
      const evaluationError = this.backendFlag().reason?.kind === 'ERROR' && !this.override;
      if (
        (next.id !== current.id && !evaluationError) ||
        (next.id === current.id && next.description !== current.description)
      ) {
        this.backend.set(next);
      }
    });
    // The page colors follow the backend that answered.
    effect(() => {
      document.body.dataset['backend'] = this.shown().id;
    });
  }

  private identify(product: ProductOffer): void {
    // Refetching the same product from another backend doesn't change the context.
    if (this.identified === product.id) return;
    this.identified = product.id;
    // Evaluate enable-purchasing for this user and this product. Waits at most 500ms; the button
    // updates through flagDetailSignal when the new flags arrive.
    identifyContext({ kind: 'multi', user: userAttributes, product: productAttributes(product) });
  }

  protected placeOrder(): void {
    if (this.purchasing().value) {
      alert('Thank you for your purchase.');
    }
  }
}

function describeReason(reason: LDEvaluationReason | null | undefined): string {
  if (!reason) return 'unknown';
  if (reason.kind === 'ERROR') return `ERROR (${reason.errorKind})`;
  if (reason.kind === 'RULE_MATCH') return `RULE_MATCH (rule ${(reason.ruleIndex ?? 0) + 1})`;
  return reason.kind;
}
