import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "Time Archive",
  description: "A 24-hour archive where every second can hold approved media.",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
