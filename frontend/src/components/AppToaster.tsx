import { Toaster } from "sonner";
import { useTheme } from "../lib/theme";

export function AppToaster() {
  const { theme } = useTheme();
  return (
    <Toaster
      theme={theme}
      position="top-center"
      richColors
      closeButton
      duration={6000}
      toastOptions={{ className: "font-sans" }}
      containerAriaLabel="Notifications"
    />
  );
}
