import {
  type LDContextCommon,
  type LDEvaluationReason,
  useBoolVariationDetail,
  useInitializationStatus,
  useLDClient,
  useStringVariationDetail,
} from '@launchdarkly/react-sdk';
import { useEffect, useRef, useState } from 'react';

import './App.css';
import {
  BACKEND_FLAG,
  BACKENDS,
  type BackendId,
  DEFAULT_BACKEND,
  chooseBackend,
  urlOverride,
} from './backend';
import { IDENTIFY_TIMEOUT_SECONDS } from './launchdarkly/launchdarkly';
import { type ProductOffer, getProduct, productAttributes } from './product';
import { user, userAttributes as defaultUserAttributes } from './user';

export const PURCHASE_FLAG = 'enable-purchasing';

const currency = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' });

/** The latest product request: which backend it went to, and what came back. */
interface Load {
  backend: BackendId;
  product?: ProductOffer;
  failed?: boolean;
}

export default function App({
  userAttributes = defaultUserAttributes,
}: {
  /** The user part of the context the SDK started with (from the bootstrap, when there was one). */
  userAttributes?: LDContextCommon;
}) {
  const client = useLDClient();
  const { status } = useInitializationStatus();
  // Fallback false: if LaunchDarkly can't be reached, ordering stays off rather than on.
  const purchasing = useBoolVariationDetail(PURCHASE_FLAG, false);
  // Fallback spring-boot: if LaunchDarkly can't be reached, everyone stays on Spring Boot.
  const backendFlag = useStringVariationDetail(BACKEND_FLAG, DEFAULT_BACKEND);
  const [override] = useState(() => urlOverride(window.location.search));
  const flagChoice = chooseBackend(backendFlag, override);
  // Routing only follows a successful evaluation. The SDK briefly reports ERROR while identify()
  // loads the new context's flags and while it reconnects; if that moved traffic, every product
  // load would bounce to Spring Boot and back. An error keeps the current backend instead
  // (Spring Boot to begin with, or the bootstrapped choice).
  const [backend, setBackend] = useState(flagChoice);
  const evaluationError = backendFlag.reason?.kind === 'ERROR' && !override;
  if (
    (flagChoice.id !== backend.id && !evaluationError) ||
    (flagChoice.id === backend.id && flagChoice.description !== backend.description)
  ) {
    setBackend(flagChoice);
  }
  // With a bootstrap, flags are ready on the first render. Without one, wait for the SDK (at most
  // 500ms), so the first request goes to the backend the flag picks, not the fallback.
  const ready = status !== 'initializing' || client.isReady();

  const [productId] = useState(() => Math.floor(Math.random() * 5) + 1);
  const [load, setLoad] = useState<Load>();
  // The product the SDK's context includes.
  const identified = useRef<number>(undefined);

  useEffect(() => {
    if (!ready) return;
    // Aborted when the backend changes, so a late answer from the old backend can't replace the
    // new one.
    const request = new AbortController();
    setLoad({ backend: backend.id });
    getProduct(productId, user, backend, request.signal)
      .then((loaded) => {
        if (request.signal.aborted) return;
        setLoad({ backend: backend.id, product: loaded });
        // Refetching the same product from another backend doesn't change the context.
        if (identified.current === loaded.id) return;
        identified.current = loaded.id;
        // Evaluate enable-purchasing for this user and this product. Waits at most 500ms; the
        // hook re-renders when the new flags arrive.
        client.identify(
          { kind: 'multi', user: userAttributes, product: productAttributes(loaded) },
          { timeout: IDENTIFY_TIMEOUT_SECONDS },
        );
      })
      .catch(() => !request.signal.aborted && setLoad({ backend: backend.id, failed: true }));
    return () => request.abort();
    // Only a different backend, not a new reason for the same one, needs a new request.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [client, ready, backend.id, productId, userAttributes]);

  // The backend that answered, or the one being asked.
  const shownId = load?.product?.backend.id ?? load?.backend ?? backend.id;
  const shown = BACKENDS[shownId];
  useEffect(() => {
    document.body.dataset.backend = shownId;
  }, [shownId]);

  const product = load?.product;
  const placeOrder = () => {
    if (purchasing.value) alert('Thank you for your purchase.');
  };

  return (
    <div className="main" data-backend={shownId}>
      <div className="backendBanner" role="status">
        <span className="backendEmoji" aria-hidden="true">
          {shown.emoji}
        </span>
        {product
          ? `Served by ${product.backend.name} ${product.backend.version}`
          : load?.failed
            ? `${shown.name} unavailable`
            : `Connecting to ${shown.name}…`}
      </div>
      <div className="row">
        <div className="column">
          {product ? (
            <div className="product">
              <div className="productImage">
                <img src={product.url} alt={product.name} />
                <div className="productDetails">
                  <h3 className="productName">{product.name}</h3>
                  <h2 className="productPrice">{currency.format(product.price)}</h2>
                  {product.discount > 0 && (
                    <p className="productOriginalPrice">
                      was {currency.format(product.originalPrice)}
                    </p>
                  )}
                </div>
              </div>
            </div>
          ) : (
            <p className="message">
              {load?.failed
                ? `Could not load a product from ${shown.name}. Is it running?`
                : 'Loading a product…'}
            </p>
          )}
        </div>
        <div className="column">
          <div className="orderFormBox">
            <div className="orderForm">
              <h3 className="orderFormTitle">React Order Form</h3>
              <ReadOnlyInput id="email" label="User's Email" value={user.email} />
              <ReadOnlyInput id="plan" label="User's Plan" value={user.plan} />
              <button
                className="orderButton"
                type="button"
                disabled={!purchasing.value}
                onClick={placeOrder}
              >
                Place order
              </button>

              <dl className="flagInfo">
                <dt>LaunchDarkly</dt>
                <dd>{describeStatus(status)}</dd>
                <dt>enable-purchasing (client)</dt>
                <dd>
                  {String(purchasing.value)} · {describeReason(purchasing.reason)}
                </dd>
                <dt>api-backend (client)</dt>
                <dd>{backend.description}</dd>
                <dt>discount-pricing (server)</dt>
                <dd>{product ? `${product.discount} · ${product.discountReason}` : '—'}</dd>
                <dt>backend (server)</dt>
                <dd>
                  {product
                    ? `${shown.emoji} ${product.backend.name} ${product.backend.version}`
                    : load?.failed
                      ? `${shown.emoji} ${shown.name} · unavailable`
                      : '—'}
                </dd>
              </dl>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

function ReadOnlyInput({ id, label, value }: { id: string; label: string; value: string }) {
  return (
    <div className="input">
      <label htmlFor={id}>{label}</label>
      <div className="orderInput">
        <input id={id} type="text" value={value} readOnly />
      </div>
    </div>
  );
}

function describeStatus(status: string): string {
  switch (status) {
    case 'complete':
      return 'connected';
    case 'timeout':
      return 'no response within 500ms, using fallback values';
    case 'failed':
      return 'failed to initialize, check the client-side ID';
    default:
      return 'initializing…';
  }
}

function describeReason(reason: LDEvaluationReason | null | undefined): string {
  if (!reason) return 'unknown';
  if (reason.kind === 'ERROR') return `ERROR (${reason.errorKind})`;
  if (reason.kind === 'RULE_MATCH') return `RULE_MATCH (rule ${(reason.ruleIndex ?? 0) + 1})`;
  return reason.kind;
}
