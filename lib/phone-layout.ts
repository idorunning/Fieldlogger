/** Chrome's desktop-site preference can give a phone a 980px layout viewport.
 * Keep controls at phone size without disabling the user's pinch zoom. Run in
 * the document head, before paint, and keep viewport heights in the same scale.
 */
export const phoneLayoutScript = `(() => {
  if (!navigator.maxTouchPoints || !matchMedia('(pointer: coarse)').matches) return;
  const root = document.documentElement;
  const fit = () => {
    const viewport = window.visualViewport;
    const visibleWidth = viewport ? viewport.width * viewport.scale : screen.width;
    const phoneWidth = Math.min(screen.width, visibleWidth);
    const widePhoneViewport = phoneWidth > 0 && phoneWidth <= 800 && innerWidth > phoneWidth * 1.2;
    const scale = widePhoneViewport ? innerWidth / phoneWidth : 1;
    root.style.setProperty('--phone-layout-scale', String(scale));
    root.style.zoom = String(scale);
  };
  fit();
  addEventListener('resize', fit, { passive: true });
  addEventListener('orientationchange', () => requestAnimationFrame(fit), { passive: true });
})();`;
