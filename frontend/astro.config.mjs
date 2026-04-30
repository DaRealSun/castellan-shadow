import { defineConfig } from 'astro/config';
import tailwind from '@astrojs/tailwind';
import cloudflare from '@astrojs/cloudflare';

export default defineConfig({
  integrations: [tailwind()],
  site: 'https://castellan-shadow.minhsonle.sunhomelab.com',
  adapter: cloudflare(),
});
