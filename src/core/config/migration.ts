import { CONFIG_SCHEMA_VERSION } from "../app-identity";
import { emptyConfig, type AppConfig, type PrintEngine, type PrinterBrand } from "./models";
import { defaultPrintEngine } from "../../printer/enginePolicy";

const ENGINES: PrintEngine[] = ["STAR_IO10", "ESC_POS"];

export function migrateConfig(raw: unknown): AppConfig {
  const base = emptyConfig(CONFIG_SCHEMA_VERSION);
  if (!raw || typeof raw !== "object") {
    return base;
  }
  const json = raw as Record<string, unknown>;
  const version = typeof json.configVersion === "number" ? json.configVersion : 1;
  const rawPrinter =
    json.printer && typeof json.printer === "object"
      ? (json.printer as Record<string, unknown>)
      : {};
  const merged: AppConfig = {
    ...base,
    ...pickKnown(json, base)
  };
  merged.printer = normalizePrinter(merged.printer, base, rawPrinter);
  merged.configVersion = CONFIG_SCHEMA_VERSION;
  if (version < CONFIG_SCHEMA_VERSION) {
    // v1 → v2: add Star engine fields with safe defaults.
  }
  return merged;
}

function normalizePrinter(
  printer: AppConfig["printer"],
  base: AppConfig,
  rawPrinter: Record<string, unknown>
): AppConfig["printer"] {
  const brand = (printer.brand || base.printer.brand) as PrinterBrand;
  const specified =
    typeof rawPrinter.printEngine === "string" && ENGINES.includes(rawPrinter.printEngine as PrintEngine);
  const printEngine = specified
    ? (rawPrinter.printEngine as PrintEngine)
    : defaultPrintEngine(brand);
  return {
    ...base.printer,
    ...printer,
    brand,
    printEngine,
    starIdentifier: printer.starIdentifier || printer.macAddress || printer.ip || "",
    profile: { ...base.printer.profile, ...(printer.profile || {}) }
  };
}

function pickKnown(json: Record<string, unknown>, base: AppConfig): AppConfig {
  return {
    configVersion: CONFIG_SCHEMA_VERSION,
    setupCompleted: Boolean(json.setupCompleted),
    orientation: (json.orientation as AppConfig["orientation"]) || base.orientation,
    division: { url: (json.division as Record<string, string>)?.url || base.division.url },
    printer: {
      ...base.printer,
      ...(json.printer as object),
      profile: {
        ...base.printer.profile,
        ...((json.printer as { profile?: object } | undefined)?.profile || {})
      }
    },
    printers: Array.isArray(json.printers) ? (json.printers as AppConfig["printers"]) : [],
    security: { ...base.security, ...(json.security as object) }
  };
}
