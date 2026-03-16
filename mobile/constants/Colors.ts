// Brand palette — matches teaneckminyanim.com
// Design language inspired by Copilot Money: clean hierarchy, rich accents,
// generous spacing, subtle depth.
const brand = '#275ED8';
const brandDark = '#1F4BAC';

const Colors = {
  light: {
    // Core
    text: '#0D1117',
    textSecondary: '#5B6878',
    textTertiary: '#9AA3AF',
    background: '#F0F2F7',
    card: '#FFFFFF',
    tint: brand,
    tintDark: brandDark,
    // Navigation
    tabIconDefault: '#9AA3AF',
    tabIconSelected: brand,
    border: '#E4E7EC',
    // Surfaces
    surfaceHover: '#F5F7FB',
    // Shadow
    shadow: 'rgba(15, 23, 42, 0.08)',
    shadowStrong: 'rgba(15, 23, 42, 0.14)',
    // Semantic badge colors per minyan type
    badge: {
      SHACHARIS:     { bg: '#EBF2FF', text: '#1A4FAD' },
      MINCHA:        { bg: '#FFF8E6', text: '#92650A' },
      MAARIV:        { bg: '#F2EEFF', text: '#5B21B6' },
      MINCHA_MAARIV: { bg: '#FEF0FA', text: '#9D174D' },
      SELICHOS:      { bg: '#FFF0F0', text: '#9B1C1C' },
      default:       { bg: '#F3F4F6', text: '#4B5563' },
    },
  },
  dark: {
    text: '#EDF0F4',
    textSecondary: '#8B96A5',
    textTertiary: '#5B6878',
    background: '#0D1117',
    card: '#161B22',
    tint: '#5B8FFF',
    tintDark: '#3D6FE8',
    tabIconDefault: '#5B6878',
    tabIconSelected: '#5B8FFF',
    border: '#21262D',
    surfaceHover: '#1C2128',
    shadow: 'rgba(0,0,0,0.4)',
    shadowStrong: 'rgba(0,0,0,0.6)',
    badge: {
      SHACHARIS:     { bg: '#162040', text: '#93B8FF' },
      MINCHA:        { bg: '#2D1F00', text: '#FCD34D' },
      MAARIV:        { bg: '#1E0F40', text: '#C4B5FD' },
      MINCHA_MAARIV: { bg: '#2D0F25', text: '#F9A8D4' },
      SELICHOS:      { bg: '#2D0F0F', text: '#FCA5A5' },
      default:       { bg: '#21262D', text: '#9CA3AF' },
    },
  },
};

export type AppColors = typeof Colors.light;
export default Colors;
