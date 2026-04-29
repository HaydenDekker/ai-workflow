import { ScannersList } from "Frontend/components/ScannersList";

/**
 * View config — excludes this route from the main layout navigation menu.
 */
export const config = { menu: { exclude: true } };

/**
 * Hidden development view for the ScannersList component.
 *
 * Route: /components/scanners-list
 *
 * This view is intentionally excluded from the main layout navigation.
 * It exists purely for rapid component prototyping and testing — access
 * it directly via URL or Storybook.
 */
export default function ScannersListView() {
  return <ScannersList />;
}
