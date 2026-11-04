#!/usr/bin/env python3
"""Check every STL: watertight/manifold, single body, fits a 220x220x250 bed."""
import glob, trimesh, numpy as np, sys
BED = (220, 220, 250); ok = True
print(f"{'part':18s} {'X':>7s} {'Y':>7s} {'Z':>7s}  water  bodies  vol(cm3)  bed-area  fits")
for f in sorted(glob.glob('stl/*.stl')):
    m = trimesh.load(f)
    ext = m.bounds[1] - m.bounds[0]
    # bed contact: area of faces lying on z=min
    zmin = m.bounds[0][2]
    on_bed = m.area_faces[(np.abs(m.triangles[:, :, 2] - zmin) < 1e-3).all(axis=1)].sum()
    fits = all(sorted(ext[:2])[i] <= sorted(BED[:2])[i] - 10 for i in range(2)) and ext[2] <= BED[2] - 10
    bodies = len(m.split(only_watertight=False))
    good = m.is_watertight and m.is_winding_consistent and m.is_volume and bodies == 1 and fits
    ok &= good
    print(f"{f[4:-4]:18s} {ext[0]:7.1f} {ext[1]:7.1f} {ext[2]:7.1f}  {str(m.is_watertight):5s}  {bodies:6d}  {m.volume/1000:8.1f}  {on_bed:8.0f}  {'OK' if good else 'FAIL'}")
sys.exit(0 if ok else 1)
