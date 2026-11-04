#!/bin/bash
# Export the 2020-extrusion parts. Needs openscad.
cd "$(dirname "$0")"
mkdir -p stl
parts="foot bench_clamp clamp_knob clamp_pad elbow_post elbow_ball head_pad head_ball phone_carriage phone_ball ball_cup ball_knob end_cap cable_clip"
for p in $parts; do
  openscad -o "stl/2020_${p}.stl" -D "part=\"$p\"" hot_spotter_2020.scad > "stl/2020_${p}.log" 2>&1 &
done
wait
for p in $parts; do
  grep -E "ERROR|Simple" "stl/2020_${p}.log" | sed "s/^/2020_$p: /"
  rm -f "stl/2020_${p}.log"
done
