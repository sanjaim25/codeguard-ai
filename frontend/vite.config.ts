import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';
export default defineConfig({plugins:[react(),tailwindcss()],server:{proxy:{'/api':'http://localhost:8080','/oauth2':'http://localhost:8080','/login/oauth2':'http://localhost:8080','/logout':'http://localhost:8080','/swagger-ui':'http://localhost:8080','/v3/api-docs':'http://localhost:8080'}},test:{include:['src/**/*.test.{ts,tsx}'],environment:'jsdom',setupFiles:['./src/test/setup.ts']}});
