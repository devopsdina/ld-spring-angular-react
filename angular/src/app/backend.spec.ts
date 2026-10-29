import contract from '../../../contracts/contexts.json';
import { DEFAULT_BACKEND, PRODUCT_TIMEOUT_MS, chooseBackend, urlOverride } from './backend';
import { userContext } from './user';

describe('chooseBackend', () => {
  it('follows the flag and passes its reason on as the routing', () => {
    expect(
      chooseBackend(
        { value: 'quarkus', reason: { kind: 'RULE_MATCH', ruleIndex: 0, ruleId: 'gold' } },
        null,
      ),
    ).toEqual({ id: 'quarkus', routing: 'flag:RULE_MATCH', description: 'quarkus · RULE_MATCH' });
  });

  it('falls back to Spring Boot for a value it does not recognize, so a flag can never pick a URL', () => {
    expect(
      chooseBackend({ value: 'https://evil.example', reason: { kind: 'FALLTHROUGH' } }, null).id,
    ).toBe(DEFAULT_BACKEND);
  });

  it('lets the URL override win over the flag', () => {
    expect(
      chooseBackend({ value: 'spring-boot', reason: { kind: 'FALLTHROUGH' } }, 'quarkus').routing,
    ).toBe('url-override');
  });
});

describe('urlOverride', () => {
  it('reads ?backend= and ignores anything but a known backend', () => {
    expect(urlOverride('?backend=quarkus')).toBe('quarkus');
    expect(urlOverride('?backend=other')).toBeNull();
  });
});

describe('product requests', () => {
  it('give up after 2s', () => {
    expect(PRODUCT_TIMEOUT_MS).toBe(2000);
  });
});

describe('contexts', () => {
  it('builds the same user context as the backends (contracts/contexts.json)', () => {
    expect(userContext).toEqual(contract.users.angular);
  });
});
