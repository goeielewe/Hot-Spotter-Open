#!/bin/bash
# Export every printable part to stl/  (needs openscad)
cd "$(dirname "$0")"
mkdir -p stl
parts="clamp_body clamp_knob clamp_pad elbow boom_head yoke cradle cradle_testfit cable_clip arm_post_end arm_post_mid arm_boom_end phone_collar phone_body phone_slider phone_follower phone_spacer base_pad"
for p in $parts; do
  openscad -o "stl/$p.stl" -D "part=\"$p\"" hot_spotter_bench_arm.scad > "stl/$p.log" 2>&1 &
done
wait
for p in $parts; do grep -E "WARNING|ERROR|Simple|Volumes" "stl/$p.log" | sed "s/^/$p: /"; rm -f "stl/$p.log"; done
