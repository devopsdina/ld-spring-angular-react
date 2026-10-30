import { version } from '../package.json';
import { applicationInfo } from './app-info';

describe('applicationInfo', () => {
  it('identifies the React app with the version from package.json', () => {
    expect(applicationInfo).toEqual({
      id: 'ld-spring-angular-react-react',
      name: 'LaunchDarkly-React-Order-Form',
      version,
    });
    expect(version).toBe('1.0.0');
  });
});
