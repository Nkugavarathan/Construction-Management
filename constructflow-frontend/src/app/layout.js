import { Inter } from "next/font/google";
import { Providers } from "./providers";
import "./globals.css";

const inter = Inter({ variable: "--font-inter", subsets: ["latin"], display: "swap" });

export const metadata = {
  title: { default: "ConstructFlow", template: "%s · ConstructFlow" },
  description: "Construction project, cost, inventory and procurement management.",
};

export default function RootLayout({ children }) {
  return (
    <html lang="en" className={`${inter.variable} h-full`}>
      <body className="min-h-full bg-background text-text">
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
