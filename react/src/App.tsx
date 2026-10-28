import {
  type LDEvaluationReason,
  useBoolVariationDetail,
  useInitializationStatus,
  useLDClient,
} from '@launchdarkly/react-sdk';
import { useEffect, useState } from 'react';

import './App.css';
import { IDENTIFY_TIMEOUT_SECONDS } from './launchdarkly/launchdarkly';
import { type ProductOffer, getProduct, productAttributes } from './product';
import { user, userAttributes } from './user';

export const PURCHASE_FLAG = 'enable-purchasing';

const currency = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' });

export default function App() {
  const client = useLDClient();
  const { status } = useInitializationStatus();
  // Fallback false: if LaunchDarkly can't be reached, ordering stays off rather than on.
  const purchasing = useBoolVariationDetail(PURCHASE_FLAG, false);

  const [productId] = useState(() => Math.floor(Math.random() * 5) + 1);
  const [product, setProduct] = useState<ProductOffer>();
  const [error, setError] = useState<string>();

  useEffect(() => {
    let active = true;
    getProduct(productId, user)
      .then((loaded) => {
        if (!active) return;
        setProduct(loaded);
        // Evaluate enable-purchasing for this user and this product. Waits at most 500ms; the
        // hook re-renders when the new flags arrive.
        client.identify(
          { kind: 'multi', user: userAttributes, product: productAttributes(loaded) },
          { timeout: IDENTIFY_TIMEOUT_SECONDS },
        );
      })
      .catch(
        () => active && setError('Could not load a product. Is the Spring Boot server running?'),
      );
    return () => {
      active = false;
    };
  }, [client, productId]);

  const placeOrder = () => {
    if (purchasing.value) alert('Thank you for your purchase.');
  };

  return (
    <div className="main">
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
            <p className="message">{error ?? 'Loading a product…'}</p>
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
                <dt>enable-purchasing</dt>
                <dd>
                  {String(purchasing.value)} · {describeReason(purchasing.reason)}
                </dd>
                {product && (
                  <>
                    <dt>discount-pricing (server)</dt>
                    <dd>
                      {product.discount} · {product.discountReason}
                    </dd>
                  </>
                )}
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
