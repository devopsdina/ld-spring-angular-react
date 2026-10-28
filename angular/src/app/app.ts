import { CurrencyPipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import type { LDEvaluationReason } from '@launchdarkly/js-client-sdk';

import { flagDetailSignal, launchDarklyStatus } from './launchdarkly/flag-signal';
import { identifyContext } from './launchdarkly/ld-client';
import { ProductOffer, ProductService, productAttributes } from './product.service';
import { user, userAttributes } from './user';

export const PURCHASE_FLAG = 'enable-purchasing';

@Component({
  selector: 'app-root',
  imports: [CurrencyPipe],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {
  protected readonly user = user;
  protected readonly status = launchDarklyStatus;
  protected readonly product = signal<ProductOffer | undefined>(undefined);
  protected readonly error = signal<string | undefined>(undefined);

  // Fallback false: if LaunchDarkly can't be reached, ordering stays off rather than on.
  protected readonly purchasing = flagDetailSignal(PURCHASE_FLAG, false);
  protected readonly purchaseReason = computed(() => describeReason(this.purchasing().reason));

  constructor() {
    const id = Math.floor(Math.random() * 5) + 1;
    inject(ProductService)
      .getProduct(id, user)
      .subscribe({
        next: (product) => {
          this.product.set(product);
          // Evaluate enable-purchasing for this user and this product. Waits at most 500ms; the
          // button updates through flagDetailSignal when the new flags arrive.
          identifyContext({
            kind: 'multi',
            user: userAttributes,
            product: productAttributes(product),
          });
        },
        error: () => this.error.set('Could not load a product. Is the Spring Boot server running?'),
      });
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
