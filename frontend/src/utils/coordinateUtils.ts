import proj4 from 'proj4'

// Register Taiwan coordinate projections
proj4.defs(
  'EPSG:3826',
  '+proj=tmerc +lat_0=0 +lon_0=121 +k=0.9999 +x_0=250000 +y_0=0 +ellps=GRS80 +towgs84=0,0,0,0,0,0,0 +units=m +no_defs',
)
proj4.defs(
  'EPSG:3828',
  '+proj=tmerc +lat_0=0 +lon_0=121 +k=0.9999 +x_0=250000 +y_0=0 +ellps=aust_SA +towgs84=-752,-358,-179,-0.0000011698,0.0000018398,0.0000009822,0.00002329 +units=m +no_defs',
)

export function wgs84ToTwd97(lng: number, lat: number): { x: number; y: number } {
  const [x, y] = proj4('EPSG:4326', 'EPSG:3826', [lng, lat])
  return { x: Math.round(x * 1000) / 1000, y: Math.round(y * 1000) / 1000 }
}

export function twd97ToWgs84(x: number, y: number): { lng: number; lat: number } {
  const [lng, lat] = proj4('EPSG:3826', 'EPSG:4326', [x, y])
  return { lng: Math.round(lng * 1e7) / 1e7, lat: Math.round(lat * 1e7) / 1e7 }
}

export function wgs84ToTwd67(lng: number, lat: number): { x: number; y: number } {
  const [x, y] = proj4('EPSG:4326', 'EPSG:3828', [lng, lat])
  return { x: Math.round(x * 1000) / 1000, y: Math.round(y * 1000) / 1000 }
}

export function twd67ToWgs84(x: number, y: number): { lng: number; lat: number } {
  const [lng, lat] = proj4('EPSG:3828', 'EPSG:4326', [x, y])
  return { lng: Math.round(lng * 1e7) / 1e7, lat: Math.round(lat * 1e7) / 1e7 }
}

export function twd97ToTwd67(x: number, y: number): { x: number; y: number } {
  const [rx, ry] = proj4('EPSG:3826', 'EPSG:3828', [x, y])
  return { x: Math.round(rx * 1000) / 1000, y: Math.round(ry * 1000) / 1000 }
}

export function twd67ToTwd97(x: number, y: number): { x: number; y: number } {
  const [rx, ry] = proj4('EPSG:3828', 'EPSG:3826', [x, y])
  return { x: Math.round(rx * 1000) / 1000, y: Math.round(ry * 1000) / 1000 }
}
