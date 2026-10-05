// Low reasoning keeps interactive photo analysis responsive; image detail stays high.
export const DEFAULT_VISION_MODEL = 'gpt-6.1-sol';
export function visionRequestOptions(configured: string | undefined, purpose: 'identify' | 'publish' | 'test') {
  const model = configured || DEFAULT_VISION_MODEL;
  return {
    model,
    ...(/^gpt-[5-9]/.test(model) ? {reasoning:{effort:'low' as const}} : {}),
    max_output_tokens: purpose === 'identify' ? 4000 : purpose === 'publish' ? 1600 : 512,
  };
}
