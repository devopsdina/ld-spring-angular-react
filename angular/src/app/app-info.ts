import { version } from '../../package.json';

/**
 * Application metadata, sent with every SDK request so LaunchDarkly can show which app and version
 * connected. The version comes from package.json; only that field is bundled.
 */
export const applicationInfo = {
  id: 'ld-spring-angular-react-angular',
  name: 'LaunchDarkly Angular Order Form',
  version,
};
