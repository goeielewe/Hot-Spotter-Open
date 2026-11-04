// Hot Spotter bench arm — 2020 extrusion
// OpenSCAD 2021.01+. Copyright (c) 2026 goeielewe. Licensed under the Apache License, Version 2.0.
//
// 20×20 mm V-slot or T-slot, 6 mm slot. Printed in PETG.
// Ball joints at the elbow and the camera head, each locked by a
// printed knob on an M5 bolt and a captive nut. The phone carriage
// slides in the post slot. No heat-set inserts (warm PETG creeps).
//
// Buy one 1000 mm stick. Cut 635 mm (post) and 350 mm (crossbar).

use <hot_spotter_bench_arm.scad>

/* [Output] */
// assembly | exploded | section |
// foot | bench_clamp | clamp_knob | clamp_pad |
// elbow_post | elbow_ball | head_pad | head_ball |
// phone_carriage | phone_ball | ball_cup | ball_knob | end_cap | cable_clip
part = "assembly";

/* [Hidden] */
$fn = 48;
eps = 0.02;

ex       = 20;
side_gap = 0.30;
bore     = ex + 2 * side_gap;   // 20.6
bolt_d   = 5.5;
nut_flats = 8.0;
nut_h    = 4.2;
wall     = 5.0;
pad_t    = 6;

ball_r    = 15;
ball_gap  = 0.35;
bump_h    = 0.40;
seat_r    = ball_r + ball_gap;
neck_d    = 12;
cup_floor = 2.2;
cup_top   = 8.2;
cup_wall  = 4.0;
ear_x     = 38;                 // long enough that the 36 mm knob misses the cup
seat_cz   = cup_floor + seat_r; // sphere centre in cup coordinates
knob_d    = 36;
knob_h    = 16;
m5_head_d = 9.2;
m5_head_h = 5.2;
ball_flat = ball_r * cos(50);   // flat-to-centre, so the flat sits on the bed
clevis_z  = ball_flat + ball_r + 12;

foot_t    = 12;
post_cut  = 635;
boom_cut  = 350;
cam_x     = 300;                // post axis -> camera centre
zk        = 33.4;               // cradle tilt axis above the lens face
sleeve_h  = 50;

module orient_up(v) {
  b = acos(v[2] / max(norm(v), 1e-9));
  c = atan2(v[1], v[0]);
  rotate([0, 0, c]) rotate([0, b, 0]) children();
}

module ball_bumps() {
  for (sy = [-1, 1]) for (ia = [0:11]) for (ib = [0:2]) {
    a = 30 + ib * 16;
    t = ia * 30 + (ib % 2) * 12;
    v = sy * [sin(a) * cos(t), cos(a), sin(a) * sin(t)];
    orient_up(v) translate([0, 0, ball_r - 0.1]) cylinder(d=1.8, h=bump_h + 0.1, $fn=8);
  }
}

module ball_print() {
  difference() {
    union() {
      sphere(r=ball_r, $fn=72);
      ball_bumps();
      translate([0, 0, ball_r - 2]) cylinder(d=neck_d, h=18, $fn=36);
    }
    translate([0, 0, -ball_r - 2])
      cylinder(r=ball_r + 2, h=ball_r * (1 - cos(50)) + 2, $fn=40);
  }
}

module ball_cup(nut=false, neck=false) {
  rim = sqrt(max(seat_r * seat_r - (seat_cz - cup_top) * (seat_cz - cup_top), 1));
  difference() {
    union() {
      cylinder(r=rim + cup_wall, h=cup_top, $fn=64);
      hull() {
        translate([rim * 0.45, 0, 0]) cylinder(d=14, h=cup_top, $fn=28);
        translate([ear_x, 0, 0]) cylinder(d=18, h=cup_top, $fn=32);
      }
    }
    translate([0, 0, seat_cz]) sphere(r=seat_r, $fn=64);
    translate([ear_x, 0, -1]) cylinder(d=bolt_d, h=cup_top + 2, $fn=22);
    if (nut)
      translate([ear_x, 0, cup_top - nut_h - 0.2])
        cylinder(d=(nut_flats + 0.35) / cos(30), h=nut_h + 1, $fn=6);
    if (neck)
      translate([0, 0, -1]) cylinder(d=neck_d + 3, h=cup_top + 2, $fn=32);
  }
}

module ball_knob() {
  difference() {
    union() {
      cylinder(d=knob_d - 8, h=knob_h, $fn=48);
      for (i = [0:5]) rotate(i * 60)
        translate([(knob_d - 8) / 2, 0, 0]) cylinder(d=8, h=knob_h, $fn=18);
    }
    translate([0, 0, -1]) cylinder(d=bolt_d, h=knob_h + 2, $fn=22);
    translate([-knob_d / 2 - 1, -m5_head_d / 2, knob_h - m5_head_h])
      cube([knob_d / 2 + 1, m5_head_d, m5_head_h + 1]);
  }
}

// Round collar. Bolts enter ONE slot (the +X face), through the wall only.
module sleeve(h=50) {
  outer = bore + 2 * wall;
  difference() {
    hull() {
      cylinder(d=outer, h=max(h - 1.8, 1), $fn=64);
      translate([0, 0, max(h - 1.8, 0)]) cylinder(d=outer - 2.6, h=1.8, $fn=64);
    }
    translate([-bore / 2, -bore / 2, -1]) cube([bore, bore, h + 2]);
    for (z = [h * 0.34, h * 0.70])
      translate([bore / 2 - 0.6, 0, z]) rotate([0, 90, 0])
        cylinder(d=bolt_d, h=wall + 2.2, $fn=18);
  }
}

module rail_pad(len=48) {
  difference() {
    hull() {
      translate([-(len / 2 - 11), 0, 0]) cylinder(d=22, h=pad_t, $fn=40);
      translate([ (len / 2 - 11), 0, 0]) cylinder(d=22, h=pad_t, $fn=40);
    }
    for (x = [-12, 12]) translate([x, 0, -1]) cylinder(d=bolt_d, h=pad_t + 2, $fn=18);
  }
}

module elbow_post() {
  union() {
    sleeve(sleeve_h);
    translate([0, 0, sleeve_h - 0.4]) ball_cup(true);
  }
}

module elbow_ball() {
  union() {
    translate([0, 0, ball_flat]) ball_print();
    translate([0, 0, ball_flat + ball_r - 2 + 18 - pad_t]) rail_pad(48);
  }
}

module head_pad() {
  union() {
    rail_pad(52);
    translate([0, 0, pad_t]) ball_cup(true);
  }
}

// Ball, then a 12 mm neck that passes the neck-side cup, then the clevis.
module head_ball() {
  gap = 14.2;
  hz = clevis_z;
  flare_z = ball_flat + seat_cz + 1.0;
  difference() {
    union() {
      translate([0, 0, ball_flat]) ball_print();
      translate([0, 0, flare_z]) cylinder(d=20, h=5, $fn=36);
      for (s = [-1, 1]) hull() {
        translate([0, s * (gap / 2 + 2.2), flare_z + 2.2]) cylinder(d=10, h=2.4, $fn=22);
        translate([0, s * (gap / 2 + 3.2), hz]) rotate([90, 0, 0])
          cylinder(d=16, h=6.4, center=true, $fn=28);
      }
    }
    translate([-14, -gap / 2, flare_z + 4.6]) cube([28, gap, 28]);
    translate([0, 0, hz]) rotate([90, 0, 0]) rotate([0, 90, 0])
      linear_extrude(44, center=true)
        hull() {
          circle(r=3.3, $fn=22);
          translate([0, 4.4]) circle(r=0.2, $fn=8);
        };
  }
}

module phone_carriage() {
  difference() {
    union() {
      hull() {
        translate([-30, 0, 0]) cylinder(d=26, h=8, $fn=36);
        translate([24, 0, 0]) cylinder(d=26, h=8, $fn=36);
      }
      // Ear points to local -Y so, once the plate is on the post, the knob is beside the phone.
      translate([36, 0, 0]) rotate([0, 0, -90]) ball_cup(true);
    }
    for (x = [-14, 14]) translate([x, 0, -1]) cylinder(d=bolt_d, h=14, $fn=18);
  }
}

module phone_ball() { head_ball(); }

module end_cap() {
  union() {
    difference() {
      union() {
        cylinder(d=26, h=2.4, $fn=48);
        translate([0, 0, 2.1]) cylinder(d=17.4, h=1.3, $fn=40);
      }
    }
    // Ribs into one pair of opposite slots, with a small grip bump.
    for (s = [-1, 1]) {
      translate([s > 0 ? 5.2 : -10.2, -2.55, 1.8]) cube([5.0, 5.1, 6.4]);
      translate([s > 0 ? 7.4 : -9.0, -2.75, 4.2]) cube([1.6, 5.5, 2.2]);
    }
  }
}

// Snaps into the 6 mm slot. Barb up, 45° lead, channel open to the side. No supports.
module cable_clip() {
  difference() {
    union() {
      hull() {
        translate([-10, 0, 0]) cylinder(d=12, h=5, $fn=32);
        translate([10, 0, 0]) cylinder(d=12, h=5, $fn=32);
      }
      translate([-2.55, -2.35, 4.6]) cube([5.1, 4.7, 2.4]);
      hull() {
        translate([-2.55, -2.35, 6.6]) cube([5.1, 4.7, 0.4]);
        translate([-4.4, -1.55, 9.0]) cube([8.8, 3.1, 1.7]);
      }
    }
    translate([-16, 0.8, 2.7]) rotate([0, 90, 0]) cylinder(d=4.8, h=32, $fn=20);
    translate([-16, 0.8, 2.7]) cube([32, 8, 6]);
  }
}

module foot() {
  difference() {
    union() {
      linear_extrude(foot_t) offset(r=16, $fn=32) offset(delta=-16) square(140, center=true);
      translate([0, 0, foot_t]) sleeve(56);
    }
    for (sx = [-1, 1], sy = [-1, 1]) translate([sx * 50, sy * 50, -1]) {
      cylinder(d=5.5, h=20, $fn=22);
      translate([0, 0, 7.2]) cylinder(d1=5.5, d2=11.2, h=3.0, $fn=22);
      translate([0, 0, 10.0]) cylinder(d=12, h=4, $fn=22);
    }
  }
}

// Print frame: the C is the silhouette, extruded up. Post bore runs along Y
// (vertical once the clamp is stood up) with a 45° roof so the horizontal
// tunnel needs no support. Nut drops in from the top face.
module bench_clamp() {
  w = 46;
  screw_x = 44;
  sleeve_d = 44;     // room for a 45° roof over the 20.6 mm bore
  y0 = 34;
  slen = 50;
  difference() {
    union() {
      linear_extrude(w)
        offset(r=7, $fn=28) offset(delta=-7)
          offset(r=-3.5, $fn=16) offset(delta=3.5) union() {
            square([20, 136]);
            translate([12, 114]) square([62, 22]);
            translate([12, 0]) square([50, 22]);
          };
      translate([-10, y0 + slen, w / 2]) rotate([90, 0, 0])
        cylinder(d=sleeve_d, h=slen, $fn=64);
    }
    // square bore, open at both ends of the sleeve, plus a 45° roof that stays inside the sleeve
    translate([-10 - bore / 2, y0 - 1, w / 2 - bore / 2]) cube([bore, slen + 2, bore]);
    translate([-10, y0 + slen + 1, w / 2 + bore / 2]) rotate([90, 0, 0])
      linear_extrude(slen + 2) polygon([[-bore / 2, 0], [bore / 2, 0], [0, bore / 2 - 0.4]]);
    for (y = [y0 + 16, y0 + 34])
      translate([-10, y, w / 2 + bore / 2 - 0.4]) cylinder(d=bolt_d, h=w, $fn=18);
    // M8 screw along Y, teardrop roof toward +Z
    translate([screw_x, -2, w / 2]) rotate([-90, 0, 0])
      linear_extrude(40) hull() {
        circle(d=8.6, $fn=28);
        translate([0, 5.5]) circle(r=0.3, $fn=8);
      };
    // nut, dropped in from the top face
    translate([screw_x, 11, w / 2]) rotate([90, 0, 0])
      cylinder(d=(13.4) / cos(30), h=8, center=true, $fn=6);
    translate([screw_x - 7, 7, w / 2]) cube([14, 8, w]);
  }
}

module clamp_knob() {
  difference() {
    union() {
      cylinder(d=22, h=16, $fn=36);
      for (i = [0:5]) rotate(i * 60)
        translate([11, 0, 0]) cylinder(d=8, h=16, $fn=18);
    }
    translate([0, 0, -1]) cylinder(d=8.6, h=20, $fn=22);
    translate([0, 0, 9.6]) cylinder(d=(13.2) / cos(30), h=8, $fn=6);
  }
}

module clamp_pad() {
  difference() {
    cylinder(d=24, h=10, $fn=36);
    translate([0, 0, 3.5]) cylinder(d=15.2, h=8, $fn=28);
  }
}

module section_joint() {
  difference() {
    union() {
      ball_cup(true);
      translate([0, 0, 2 * seat_cz]) rotate([180, 0, 0]) ball_cup(false, true);
      translate([0, 0, seat_cz]) {
        sphere(r=ball_r, $fn=64);
        ball_bumps();
        translate([0, 0, ball_r - 2]) cylinder(d=neck_d, h=22, $fn=28);
      }
    }
    translate([-90, 0, -10]) cube([180, 90, 80]);
  }
}

module exploded_joint() {
  color("orange") ball_cup(true);
  color([0.95, 0.55, 0.2]) translate([0, 0, seat_cz + 22]) {
    sphere(r=ball_r, $fn=56);
    ball_bumps();
  }
  color([1, 0.78, 0.4]) translate([0, 0, 2 * seat_cz + 36]) rotate([180, 0, 0]) ball_cup(false, true);
  color([0.25, 0.45, 0.75]) translate([ear_x, 0, 2 * seat_cz + 58]) ball_knob();
}

// Preview only. Centred square bar with a 6 mm slot on each face.
module extrusion(len) {
  color([0.74, 0.76, 0.80])
    linear_extrude(len, center=true) difference() {
      square(ex, center=true);
      circle(d=4.3, $fn=24);
      for (a = [0:90:270]) rotate(a) {
        translate([0, ex / 2 - 6.2]) square([6.0, 7.4], center=true);
        translate([0, ex / 2 - 4.6]) square([10.6, 3.4], center=true);
      }
    };
}

module cable(dia, path) {
  for (i = [0:len(path) - 2]) hull() {
    translate(path[i]) sphere(d=dia, $fn=10);
    translate(path[i + 1]) sphere(d=dia, $fn=10);
  }
}

module assembly() {
  cup_z = foot_t + post_cut - 0.4;
  elbow_sphere = cup_z + seat_cz;
  boom_under = elbow_sphere + (ball_r - 2 + 18);
  boom_cz = boom_under + ex / 2;
  head_sphere = boom_under - pad_t - seat_cz;
  hole_z = head_sphere - (ball_r + 12);
  lens_z = hole_z - zk;
  echo("lens_mm", lens_z);
  echo("boom_under", boom_under);
  echo("reach_mm", cam_x);

  color([0.72, 0.58, 0.40]) translate([-120, -190, -18]) cube([520, 380, 18]);
  color("orange") foot();
  translate([0, 0, foot_t + post_cut / 2]) extrusion(post_cut);

  // Elbow. Ear turned to +Y so the knob sits beside the crossbar.
  color("orange") translate([0, 0, foot_t]) rotate([0, 0, 90]) elbow_post();
  color([0.95, 0.55, 0.2])
    translate([0, 0, elbow_sphere - ball_flat]) elbow_ball();
  color("orange")
    translate([0, 0, cup_z + 2 * seat_cz]) rotate([0, 0, 90]) rotate([180, 0, 0])
      ball_cup(false, true);
  color([0.25, 0.45, 0.75])
    translate([0, ear_x, cup_z + 2 * seat_cz]) ball_knob();

  x0 = -24;
  translate([x0 + boom_cut / 2, 0, boom_cz]) rotate([0, 90, 0]) extrusion(boom_cut);
  // caps: ribs point into the bar, flange outside
  color("orange") translate([x0 - 2.6, 0, boom_cz]) rotate([0, 90, 0]) end_cap();
  color("orange") translate([x0 + boom_cut + 2.6, 0, boom_cz]) rotate([0, -90, 0]) end_cap();

  // Camera head. Pad bolts to the underside; the neck hangs down through the lower cup.
  color("orange") translate([cam_x, 0, boom_under]) rotate([180, 0, 0]) head_pad();
  color([0.95, 0.55, 0.2])
    translate([cam_x, 0, head_sphere + ball_flat]) rotate([180, 0, 0]) head_ball();
  color("orange")
    translate([cam_x, 0, head_sphere - seat_cz]) ball_cup(false, true);
  color([0.25, 0.45, 0.75])
    translate([cam_x + ear_x, 0, head_sphere - seat_cz]) rotate([180, 0, 0]) ball_knob();
  translate([cam_x, 0, hole_z - zk]) {
    color("orange") cradle_use();
    camera_mock();
  }

  phone_mounted(250);

  color("orange") {
    for (z = [150, 400, 530])
      translate([0, ex / 2, z]) rotate([90, 0, 0]) translate([0, 0, -4.6]) cable_clip();
    for (x = [70, 180])
      translate([x, ex / 2, boom_cz]) rotate([90, 0, 0]) translate([0, 0, -4.6]) cable_clip();
    translate([cam_x - 70, -ex / 2, boom_cz]) rotate([-90, 0, 0]) translate([0, 0, -4.6]) cable_clip();
  }
  color([0.12, 0.38, 0.75]) cable(3.6, [
    [cam_x, 16, lens_z + 8],
    [cam_x - 40, 16, boom_under - 6],
    [40, 16, boom_cz],
    [0, 16, 560],
    [0, 16, 180],
    [-30, 16, 40]
  ]);
}

// Phone carriage on the front face of the post. Height cz is the plate origin.
module phone_mounted(cz) {
  translate([-ex / 2, 0, cz]) rotate([0, -90, 0]) color("orange") phone_carriage();
  // (x,y,z) through rotZ-90 then rotY-90 lands on (-z, -x, y)
  C = [-ex / 2 - seat_cz, 0, cz + 36];
  color([0.95, 0.55, 0.2])
    translate(C + [ball_flat, 0, 0]) rotate([0, -90, 0]) rotate([0, 0, -90]) phone_ball();
  F = C + [-seat_cz, 0, 0];
  color("orange") multmatrix([
      [0, 0, 1, F[0]],
      [-1, 0, 0, F[1]],
      [0, -1, 0, F[2]],
      [0, 0, 0, 1]
    ]) ball_cup(false, true);
  color([0.25, 0.45, 0.75]) multmatrix([
      [0, 0, -1, F[0]],
      [-1, 0, 0, F[1] - ear_x],
      [0, 1, 0, F[2]],
      [0, 0, 0, 1]
    ]) ball_knob();
  hole = C + [-(ball_r + 12), 0, 0];
  grip_hole = [35, 8, -16];
  // grip (x,y,z) -> (-z, -x, y), screen (+Z) toward the user
  Rg = [-grip_hole[2], -grip_hole[0], grip_hole[1]];
  t = hole - Rg;
  multmatrix([
    [0, 0, -1, t[0]],
    [-1, 0, 0, t[1]],
    [0, 1, 0, t[2]],
    [0, 0, 0, 1]
  ]) {
    phone_grip(78, true);
    phone_mock(78);
  }
}

if (part == "assembly")       assembly();
if (part == "exploded")       exploded_joint();
if (part == "section")        section_joint();
if (part == "foot")           foot();
if (part == "bench_clamp")    bench_clamp();
if (part == "clamp_knob")     clamp_knob();
if (part == "clamp_pad")      clamp_pad();
if (part == "elbow_post")     elbow_post();
if (part == "elbow_ball")     elbow_ball();
if (part == "head_pad")       head_pad();
if (part == "head_ball")      head_ball();
if (part == "phone_carriage") phone_carriage();
if (part == "phone_ball")     phone_ball();
if (part == "ball_cup")       ball_cup(false, true);
if (part == "ball_knob")      ball_knob();
if (part == "end_cap")        end_cap();
if (part == "cable_clip")     cable_clip();
