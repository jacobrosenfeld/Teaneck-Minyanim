import { Redirect, useLocalSearchParams } from 'expo-router';

/**
 * Legacy route. Shul detail lives inside the Shuls tab at /shuls/[id].
 * Kept only as a redirect so older links (teaneckminyanim://shul/<slug>,
 * web /shul/<slug>) open the in-tab detail instead of a separate flow.
 */
export default function LegacyShulRedirect() {
  const { id } = useLocalSearchParams<{ id: string }>();
  return <Redirect href={{ pathname: '/shuls/[id]', params: { id } } as never} withAnchor />;
}
