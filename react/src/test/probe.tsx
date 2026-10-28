import {
  type LDReactClient,
  useBoolVariationDetail,
  useInitializationStatus,
  useLDClient,
} from '@launchdarkly/react-sdk';

/** Renders a flag's detail and the init status, and hands the client to the test. */
export function Probe({
  flag,
  onClient,
}: {
  flag: string;
  onClient?: (client: LDReactClient) => void;
}) {
  const client = useLDClient();
  onClient?.(client);
  const { status } = useInitializationStatus();
  const detail = useBoolVariationDetail(flag, false);
  return (
    <p>
      <span data-testid="status">{status}</span>{' '}
      <span data-testid="value">{String(detail.value)}</span>{' '}
      <span data-testid="reason">
        {detail.reason?.kind === 'ERROR' ? `ERROR ${detail.reason.errorKind}` : detail.reason?.kind}
      </span>
    </p>
  );
}
