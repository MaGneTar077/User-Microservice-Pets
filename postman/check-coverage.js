#!/usr/bin/env node
/**
 * Compara los endpoints reales de los @RestController contra los requests de
 * MyAnimaLogUser.postman_collection.json. Reporta faltantes (en codigo, no en la
 * coleccion) y sobrantes (en la coleccion, no en el codigo). Exit code 1 si hay
 * alguno. No depende de paquetes externos.
 * Uso: node postman/check-coverage.js
 */
"use strict";

const fs = require("fs");
const path = require("path");

const CONTROLLERS_DIR = path.join(
  __dirname,
  "..",
  "src",
  "main",
  "java",
  "user",
  "microservice",
  "pets",
  "infrastructure",
  "controllers"
);
const COLLECTION_FILE = path.join(__dirname, "MyAnimaLogUser.postman_collection.json");

const MAPPING_ANNOTATIONS = {
  GetMapping: "GET",
  PostMapping: "POST",
  PutMapping: "PUT",
  DeleteMapping: "DELETE",
  PatchMapping: "PATCH",
};

// Normaliza una ruta a una lista de segmentos donde cualquier placeholder
// ({id}, {{user_id}}) se reemplaza por "*", para poder comparar una ruta de
// Spring contra una de Postman aunque usen nombres de variable distintos.
function normalize(routePath) {
  return routePath
    .split("/")
    .filter(Boolean)
    .map((seg) => (/^\{\{.*\}\}$/.test(seg) || /^\{.*\}$/.test(seg) ? "*" : seg));
}

function key(method, routePath) {
  return method + " " + normalize(routePath).join("/");
}

function extractCodeEndpoints() {
  const endpoints = [];
  const files = fs.readdirSync(CONTROLLERS_DIR).filter((f) => f.endsWith(".java"));

  for (const file of files) {
    const src = fs.readFileSync(path.join(CONTROLLERS_DIR, file), "utf8");

    const classMappingMatch = src.match(/@RequestMapping\(\s*"([^"]*)"\s*\)\s*\n(?:[^\n]*\n)*?\s*public class/);
    const classPrefix = classMappingMatch ? classMappingMatch[1] : "";

    const methodRegex = /@(GetMapping|PostMapping|PutMapping|DeleteMapping|PatchMapping)\s*\(\s*(?:value\s*=\s*)?"([^"]*)"/g;
    let m;
    while ((m = methodRegex.exec(src)) !== null) {
      const httpMethod = MAPPING_ANNOTATIONS[m[1]];
      const subPath = m[2];
      const fullPath = ("/" + classPrefix + "/" + subPath).replace(/\/+/g, "/").replace(/\/$/, "") || "/";
      endpoints.push({ method: httpMethod, routePath: fullPath, source: file });
    }
  }
  return endpoints;
}

function extractCollectionEndpoints() {
  const collection = JSON.parse(fs.readFileSync(COLLECTION_FILE, "utf8"));
  const endpoints = [];

  function walk(items) {
    for (const item of items) {
      if (item.item) {
        walk(item.item);
        continue;
      }
      if (item.request) {
        const method = item.request.method;
        const pathSegments = item.request.url && item.request.url.path ? item.request.url.path : [];
        const routePath = "/" + pathSegments.join("/");
        endpoints.push({ method, routePath, name: item.name });
      }
    }
  }
  walk(collection.item);
  return endpoints;
}

function main() {
  const codeEndpoints = extractCodeEndpoints();
  const collectionEndpoints = extractCollectionEndpoints();

  const codeKeys = new Map();
  for (const e of codeEndpoints) {
    codeKeys.set(key(e.method, e.routePath), e);
  }

  const collectionKeySet = new Set(collectionEndpoints.map((e) => key(e.method, e.routePath)));

  const missing = [...codeKeys.entries()].filter(([k]) => !collectionKeySet.has(k));
  const codeKeySet = new Set(codeKeys.keys());
  const extra = collectionEndpoints.filter((e) => !codeKeySet.has(key(e.method, e.routePath)));

  console.log("Endpoints en el codigo: " + codeKeys.size);
  console.log("Requests en la coleccion: " + collectionEndpoints.length + " (endpoints unicos cubiertos: " + collectionKeySet.size + ")");
  console.log("");

  console.log("Faltantes (en codigo, no en la coleccion): " + missing.length);
  for (const [, e] of missing) {
    console.log("  - " + e.method + " " + e.routePath + " (" + e.source + ")");
  }

  console.log("");
  console.log("Sobrantes (en la coleccion, no en el codigo): " + extra.length);
  for (const e of extra) {
    console.log("  - " + e.method + " " + e.routePath + " (\"" + e.name + "\")");
  }

  if (missing.length > 0 || extra.length > 0) {
    process.exitCode = 1;
  } else {
    console.log("");
    console.log("OK: cobertura completa (0 faltantes, 0 sobrantes).");
  }
}

main();
