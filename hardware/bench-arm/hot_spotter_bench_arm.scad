// =====================================================================
//  Hot Spotter bench arm  -  parametric USB-C thermal camera mount
//  OpenSCAD 2021.01+.  Copyright (c) 2026 goeielewe. Licensed under the Apache License, Version 2.0.
//
//  Pick a part with -D 'part="clamp_body"' (see list below) or set it
//  here.  All dimensions in mm.  Parts are modelled in their PRINT
//  orientation (flat face on the bed, no supports); "assembly" places
//  them in use position on a bench edge.
// =====================================================================

/* [Output] */
// assembly | assembly_printed | cradle_view | clamp_view | clamp_body | clamp_knob |
// clamp_pad | elbow | boom_head | yoke | cradle | cradle_testfit |
// cable_clip | arm_post_end | arm_post_mid | arm_boom_end |
// phone_view | phone_section | phone_collar | phone_body | phone_slider |
// phone_follower | phone_spacer | base_pad | pad_view | pad_section | assembly_pad
part = "assembly";

/* [Bench] */
bench_thickness  = 38;   // your bench edge (only used for the preview)
bench_min        = 10;   // thinnest edge the clamp must grip
bench_max        = 70;   // thickest edge the clamp must grip
lip_depth        = 32;   // usable depth under the edge before an obstruction
lower_jaw_reach  = 26;   // how far the lower jaw goes under the edge (<= lip_depth-4)
top_jaw_reach    = 55;   // how far the top jaw goes over the bench top

/* [Arm geometry] */
cam_height  = 610;   // bench top -> camera lens face, camera aimed straight down
boom_reach  = 300;   // post axis -> camera centre, over the bench
tube_d      = 23.42; // 3/4" EMT outside diameter (1" dowel = 25.4)
tube_clear  = 0.5;   // diametral clearance tube -> socket bore

/* [Camera: USB-C thermal camera USB-C] */
cam_L   = 68;    // long edge (plug sits in the middle of one long edge)
cam_W   = 34;    // short edge
cam_T   = 14;    // thickness
fit     = 0.2;   // clearance per side in the cradle pocket
rib_h   = 0.35;  // crush-rib height on the end walls (friction fit)
lip_w   = 2.0;   // how far the retaining lips reach over the lens face at each end
lip_t   = 1.6;   // lip thickness
bottom_open = 50; // opening in the bottom (charge-port) edge, centred

/* [Phone holder (on the upright)] */
phone_w_min   = 58;    // narrowest phone (in its case) the grip must hold
phone_w_max   = 100;   // widest phone (in its case)
phone_on_post = 230;   // collar bottom above the clamp's top face (preview only)
phone_yaw     = 0;     // collar rotation on the post in the preview
// steel compression spring, Century Spring 3CS (2 used end to end)
spr_od    = 7.92;  // 0.312" outside diameter
spr_free  = 38.1;  // 1.500" free length
spr_solid = 7.11;  // solid height
spr_rate  = 0.315; // 1.8 lb/in = 0.315 N/mm
spr_max   = 30.48; // max recommended deflection per spring

/* [Base pad (optional screw-down mount instead of the clamp)] */
pad_W      = 140;  // square flange, side length
pad_r      = 14;   // flange corner radius
pad_t      = 12;   // flange thickness
pad_screw  = 57;   // screw holes at (+-pad_screw, +-pad_screw) from the post axis
pad_hole   = 5.5;  // clearance for #10 / M5 (also fine for #8)
pad_csk    = 11.0; // countersink diameter (90 deg)
pad_cbore  = 4;    // screw heads sit this far below the flange top (more bite for 1" screws)
pad_sock_L = 70;   // socket height (taller than the clamp's 50: lower contact forces)
pad_slit   = 34;   // slit + pinch bolts only above this (the ribbed root stays a solid ring)
pad_relief = 5;    // round relief hole at the bottom of the slit
pad_gus_t  = 10;   // rib thickness
pad_gus_h  = [30, 34, 26, 34, 26]; // rib height up the socket: +-X, +Y, -Y (ear side), +Y corners, -Y corners
pad_rho    = 10;   // blend radius where the ribs run into the socket and the flange
pad_fil    = 8;    // concave fillet radius at the socket root
pad_label  = true; // engraved name on the flange (false for the FEA mesh)

/* [Print] */
tol = 0.2;       // general hole tolerance

/* [Hidden] */
$fn = 64;
eps = 0.01;

// ---------- hardware -------------------------------------------------
m8_d = 8.6;   m8_flats = 13.0; m8_nut_h = 6.5;
m6_d = 6.6;   m6_flats = 10.0; m6_head_h = 4.0;
m5_d = 5.5;   m5_flats = 8.0;  m5_nut_h = 4.0;
m3_pilot = 2.6;

// ---------- socket (tube pinch clamp) --------------------------------
sock_W  = 40;      // block width
sock_H  = 50;      // block height incl. pinch ears
sock_L  = 50;      // socket depth
sock_zc = 20;      // bore centre height in the block
sock_bore = tube_d + tube_clear;

// ---------- clamp ----------------------------------------------------
clamp_w  = sock_W;
spine_t  = 14;
top_t    = 14;
low_t    = 18;
rubber_t = 1.5;   // stick-on rubber on the jaws
pad_d = 24; pad_h = 10; pad_pocket_d = 15.4; pad_pocket_h = 7; // pocket clears the acorn nut's hex corners so it swivels
acorn_h = 15; acorn_exposed = acorn_h - pad_pocket_h;
throat  = bench_max + 2*rubber_t + pad_h + acorn_exposed + 2;
screw_x = 15;     // screw axis, measured in from the bench front face (pad clears the apron bend)
sock_x  = 15;     // post axis, measured in from the bench front face
knob_d  = 24; knob_h = 18;

// ---------- pan / tilt -----------------------------------------------
plate_t = 10; pan_off = 22;           // boom_head pan plate
yoke_disk_d = 34; yoke_disk_t = 10;
tilt_drop = 19;                       // disk bottom -> tilt axis
ear_t = 5; ear_w = 22;
tongue_t = 12; tongue_h = 16;
ridge_h = 0.8; ridge_n = 24; ridge_ri = 4.4; ridge_ro = 10.5;
ear_gap = tongue_t/2 + ridge_h + 0.2; // half gap between the ears

// ---------- cradle ---------------------------------------------------
pl = cam_L + 2*fit; pw = cam_W + 2*fit; pt = cam_T + 2*fit;
c_wall = 3; back_t = 3;
y_top  = pw/2 + 5;                     // cradle extends past the plug edge
keep_y = pw/2 + 2;                     // keeper-screw axis
corner_w = (pl - bottom_open)/2;
zk = pt + back_t + tongue_h;           // tilt axis above the lens face

// ---------- derived lengths ------------------------------------------
elbow_stop = 22;                       // tube end -> elbow corner centre
boom_z  = cam_height + sock_zc + yoke_disk_t + tilt_drop + zk;
post_len = boom_z - elbow_stop - top_t;
boom_len = boom_reach - elbow_stop - 3 - pan_off;
pan_x    = sock_x + boom_reach;

// printed (segmented) arm
oct_flats = tube_d*cos(22.5);
lap = 50; seg_max = 205;
function nseg(L) = max(2, ceil((L - lap)/(seg_max - lap)));
function seglen(L) = (L + (nseg(L)-1)*lap)/nseg(L);

// bolt-length check for the clamp screw (M8 x 120)
m8_len = 120;
knob_stack = knob_h + m8_nut_h;        // head inside knob + lock nut on top
free_thread = m8_len - 10 - knob_stack - low_t; // 10 = engagement in acorn nut
need_thread = throat - bench_min - 2*rubber_t - pad_h - acorn_exposed;

echo(str("POST tube length (EMT cut) = ", post_len, " mm"));
echo(str("BOOM tube length (EMT cut) = ", boom_len, " mm"));
echo(str("boom axis height = ", boom_z, " mm, throat = ", throat, " mm"));
echo(str("printed post: ", nseg(post_len), " segments of ", seglen(post_len), " mm"));
echo(str("printed boom: ", nseg(boom_len), " segments of ", seglen(boom_len), " mm"));
echo(str("M8 screw: free thread ", free_thread, " mm, needed ", need_thread, " mm"));
assert(lower_jaw_reach <= lip_depth - 4, "lower jaw reaches too far under the edge");
assert(screw_x + knob_d/2 <= lip_depth - 4, "knob would hit the panel behind the lip");
assert(free_thread >= need_thread, "M8 screw too short for bench_min");
assert(seglen(post_len) <= seg_max && seglen(boom_len) <= seg_max);

// ---------- phone holder ---------------------------------------------
// grip frame: X along the rail (fixed jaw face at x=0, opens toward +X),
// Y up the phone (y=0 = phone's bottom edge), Z out of the screen side
// (z=0 = back of the grip, z=pb_z = face the phone rests on).
spr_n     = 2;                          // springs in series
stack_free  = spr_n*spr_free;
stack_solid = spr_n*spr_solid;
stack_rate  = spr_rate/spr_n;
jaw_t   = 6;                            // jaw thickness (X)
jaw_h   = 17;                           // jaw height above the rest face
lip_in  = 3.5;                          // how far the lip leans over the phone
ledge   = 6;                            // bottom ledge reach (phone sits on it)
pb_back = 3; pb_front = 4;              // walls behind / in front of the spring channel
sl_h    = spr_od + 1.2;                 // slider thickness (Z)
win_w   = spr_od + 1.0;                 // spring window width (Y)
sl_w    = win_w + 7;                    // slider width (Y)
ch_h    = sl_h + 0.5; ch_w = sl_w + 0.5;
pb_z    = pb_back + ch_h + pb_front;    // grip thickness
pb_y0   = -3; pb_H = 37;                // grip spans y = -3 .. 34 (below the volume keys)
ch_yc   = 17;                           // channel centre
zs0     = pb_back + 0.25;               // slider underside
G_min   = phone_w_min + 2*rubber_t;     // jaw-to-jaw (plastic) at the narrowest phone
G_max   = phone_w_max + 2*rubber_t;
spacer_len = 20;                        // range spacer: in = small phones, out = big
travel  = G_max - G_min - spacer_len;   // useful travel per range (22)
over    = 6;                            // extra opening to get the phone in
preload = 20;                           // stack compression at the narrow end of a range
G_closed = 57;                          // jaws touching the stop (body end)
pb_xR   = G_closed;
f_len   = 8;                            // follower block length
x_p     = 50;                           // follower screw (M3x16)
w0      = 4;                            // slider: left end -> spring window
Ls_face = stack_free - preload + (G_min + spacer_len) + w0 - x_p + f_len/2; // slider left end -> jaw face
w1      = x_p + f_len/2 - G_closed + Ls_face;  // window right end (follower fits at closed)
pb_xL   = G_closed - Ls_face - 1.5 - 3;
x_t     = 35;                           // tilt tongue position along the grip
tilt_y  = 8;                            // tilt axis height (Y) - tongue tangent to the bed
tilt_z  = -16;                          // tilt axis behind the back face
// collar
col_H  = 36; col_wall = 5;
col_Ro = sock_bore/2 + col_wall;
col_px = col_Ro + 22;                   // swivel bolt, out from the tube axis
col_ear = 8;                            // pinch ear thickness each side of the slit

function stack_len(G, spacer) = (x_p - f_len/2) - (G - Ls_face + w0) - (spacer ? spacer_len : 0);
function grip_N(G, spacer) = stack_rate*(stack_free - stack_len(G, spacer));
echo(str("PHONE grip: range A (spacer in) ", phone_w_min, "-", phone_w_min + travel, " mm, ",
         grip_N(G_min, true), "-", grip_N(G_min + travel, true), " N"));
echo(str("PHONE grip: range B (spacer out) ", phone_w_min + spacer_len, "-", phone_w_max, " mm, ",
         grip_N(G_min + spacer_len, false), "-", grip_N(G_max, false), " N"));
echo(str("PHONE grip: per-spring deflection at full open ", (stack_free - stack_len(G_max + over, false))/spr_n,
         " of ", spr_max, " mm; stack ", stack_len(G_max + over, false), " mm (solid ", stack_solid, ")"));
echo(str("PHONE slider length ", Ls_face + jaw_t, " mm, grip body ", pb_xR - pb_xL, " mm"));
assert(travel >= 0, "spacer longer than the width range");
assert(stack_len(G_max + over, false) >= 1.15*stack_solid, "springs go solid before full opening");
assert((stack_free - stack_len(G_max + over, false))/spr_n <= 0.85*spr_max, "springs over-deflected");
assert(stack_len(G_closed, true) >= 1.15*stack_solid, "springs solid with the spacer in, jaws closed");
echo(str("PHONE grip: empty + spacer out, springs are ", stack_len(G_closed, false) - stack_free, " mm loose (normal)"));
assert(x_p - 2.75 > x_t + ear_gap + ear_t && -tilt_z - ear_w/2 > 2.4 + 1, "yoke ear hits the follower screw head");

// =====================================================================
//  helpers
// =====================================================================
module teardrop2d(d) { r = d/2; hull() { circle(r=r); rotate(45) square(r); } }

// bore along +Y from y0 to y1, teardrop tip pointing +Z (print up)
module teardrop_y(d, y0, y1) {
  translate([0, y1, 0]) rotate([90,0,0]) linear_extrude(y1 - y0) teardrop2d(d);
}
module hex_prism_x(flats, h) { // hex along +X, vertex up (prints clean)
  rotate([0,90,0]) cylinder(d=flats/cos(30), h=h, $fn=6);
}
module ridge_ring(phase=0) {   // radial locking teeth, +Z outward
  for (i=[0:ridge_n-1]) {
    a0 = phase + i*360/ridge_n; a1 = a0 + 360/ridge_n; am = (a0+a1)/2;
    hull() for (r=[ridge_ri, ridge_ro]) {
      for (a=[a0,a1]) for (z=[-0.3,0]) translate([r*cos(a), r*sin(a), z]) cube(0.02, center=true);
      translate([r*cos(am), r*sin(am), ridge_h]) cube(0.02, center=true);
    }
  }
}

// tube socket with slit + two M5 pinch bolts. Print frame:
// axis along +Y (y=0 closed side .. y=L open end), bore centre z=sock_zc,
// slit and ears on top (+Z).
module socket_block(L=sock_L, bore_from=0, slit_from=5, vbore=false) {
  difference() {
    translate([-sock_W/2, 0, 0]) cube([sock_W, L, sock_H]);
    socket_cuts(L, bore_from, slit_from, vbore);
  }
}
// vbore=true: plain round bore, for a socket printed standing up (base pad)
module socket_cuts(L, bore_from, slit_from, vbore=false) {
  if (vbore) translate([0, bore_from, sock_zc]) rotate([-90,0,0]) cylinder(d=sock_bore, h=L - bore_from + 1, $fn=96);
  else teardrop_y(sock_bore, bore_from, L + 1);
  translate([0, L, sock_zc]) rotate([-90,0,0]) cylinder(d1=sock_bore, d2=sock_bore+3, h=1.6); // lead-in
  translate([-1, slit_from, sock_zc]) cube([2, L - slit_from + 1, sock_H]);
  zb = sock_H - 6.5;
  for (f=[0.3, 0.78]) {
    yb = slit_from + (L - slit_from)*f;
    translate([-sock_W/2-1, yb, zb]) rotate([0,90,0]) cylinder(d=m5_d, h=sock_W+2);
    translate([sock_W/2-3, yb, zb]) rotate([0,90,0]) cylinder(d=9.6, h=5);          // head
    translate([-sock_W/2-1, yb, zb]) hex_prism_x(m5_flats+0.3, 1+m5_nut_h+0.5);       // nut trap
  }
  // chamfer the two top edges of the block
  for (s=[-1,1]) translate([s*sock_W/2, -1, sock_H]) rotate([0,45,0]) cube([8, L+2, 8], center=false);
  for (s=[-1,1]) translate([s*sock_W/2, L/2, sock_H]) rotate([0,45,0]) cube([8, L+4, 8], center=true);
}

// =====================================================================
//  1. clamp body  (print on its side, C-profile flat on the bed)
//  print frame: X = in from bench front face, Y = up (in use), Z = width
// =====================================================================
module clamp_profile() {
  offset(r=3) offset(delta=-3) offset(r=-3) offset(delta=3) union() {
    translate([-spine_t, -(throat+low_t)]) square([spine_t, throat+low_t+top_t]);
    translate([-spine_t, 0]) square([spine_t+top_jaw_reach, top_t]);
    translate([-spine_t, -(throat+low_t)]) square([spine_t+lower_jaw_reach, low_t]);
  }
}
module clamp_body() {
  difference() {
    union() {
      linear_extrude(clamp_w) clamp_profile();
      translate([sock_x, top_t - eps, 0]) socket_block(sock_L, 0, 6);
    }
    // M8 screw hole through the lower jaw (vertical in use)
    translate([screw_x, 0, clamp_w/2]) teardrop_y(m8_d, -(throat+low_t)-1, -throat+1);
    // captive M8 nut, dropped in from the top face of the print
    ny = -(throat + low_t/2);
    translate([screw_x, ny, clamp_w/2]) rotate([90,0,0]) rotate(30)   // corners up/down, so it drops in flats-first
      cylinder(d=(m8_flats+0.4)/cos(30), h=m8_nut_h+0.4, center=true, $fn=6);
    translate([screw_x - (m8_flats+0.4)/2, ny - (m8_nut_h+0.4)/2, clamp_w/2])
      cube([m8_flats+0.4, m8_nut_h+0.4, clamp_w]);
    // label
    translate([-spine_t/2, -(throat+low_t)/2, clamp_w-0.6]) rotate(90)
      linear_extrude(1) text("HOT SPOTTER", size=6, halign="center", valign="center");
  }
}

// =====================================================================
//  2. knob (print hex pocket up) and 3. swivel pad (print rubber face down)
// =====================================================================
module clamp_knob() {
  difference() {
    cylinder(d=knob_d, h=knob_h);
    for (i=[0:7]) rotate(i*45) translate([knob_d/2+1.6, 0, -1]) cylinder(r=3, h=knob_h+2);
    translate([0,0,-1]) cylinder(d=m8_d, h=knob_h+2);
    translate([0,0,knob_h-5.8]) cylinder(d=(m8_flats+0.3)/cos(30), h=6, $fn=6);
    translate([0,0,-eps]) cylinder(d1=m8_d+1.6, d2=m8_d, h=0.8);
    translate([0,0,knob_h-0.8]) difference() { cylinder(d=knob_d+2, h=1); cylinder(d1=knob_d, d2=knob_d-1.6, h=0.81); }
  }
}
module clamp_pad() {   // print frame: rubber face on the bed, pocket up
  difference() {
    union() {
      cylinder(d=pad_d, h=pad_h - 2);
      translate([0,0,pad_h-2]) cylinder(d1=pad_d, d2=pad_d-3, h=2);
    }
    translate([0,0,pad_h - pad_pocket_h]) cylinder(d=pad_pocket_d, h=pad_pocket_h+1);
    translate([0,0,pad_h - pad_pocket_h]) scale([1,1,0.25]) sphere(d=pad_pocket_d);   // shallow seat for the dome
  }
  // three small snap bumps just inside the mouth
  for (a=[0,120,240]) rotate(a) translate([pad_pocket_d/2, 0, pad_h-1.4]) sphere(d=1.2, $fn=12);
}

// =====================================================================
//  4. elbow (print flat, both bores horizontal with teardrop roofs)
// =====================================================================
module elbow() {
  union() {
    difference() {
      translate([-sock_W/2, -sock_W/2, 0]) cube([sock_W, sock_W, sock_H]);
      for (s=[-1,1]) translate([s*sock_W/2, 0, sock_H]) rotate([0,45,0]) cube([8, sock_W+2, 8], center=true);
    }
    translate([0, sock_W/2 - eps, 0]) socket_block(sock_L, elbow_stop - sock_W/2, 5);
    rotate([0,0,-90]) translate([0, sock_W/2 - eps, 0]) socket_block(sock_L, elbow_stop - sock_W/2, 5);
  }
}

// =====================================================================
//  5. boom head: socket + pan plate (print as modelled)
// =====================================================================
module boom_head() {
  difference() {
    union() {
      translate([0, sock_L, 0]) mirror([0,1,0]) socket_block(sock_L, 3, 5);
      hull() {
        translate([-sock_W/2, sock_L - 1, 0]) cube([sock_W, 1, plate_t]);
        translate([0, sock_L + pan_off, 0]) cylinder(r=17, h=plate_t);
      }
      hull() { // small fillet under the wall
        translate([-sock_W/2+4, sock_L - 1, 0]) cube([sock_W-8, 1, plate_t+5]);
        translate([-sock_W/2+4, sock_L - 1, 0]) cube([sock_W-8, 6, plate_t]);
      }
    }
    translate([0, sock_L + pan_off, -1]) cylinder(d=m6_d, h=plate_t+2);
  }
}

// =====================================================================
//  6. yoke (modelled in use, exported upside down)
// =====================================================================
module yoke_ear() {
  translate([ear_gap, 0, 0]) hull() {
    translate([0, -ear_w/2, -yoke_disk_t - 1]) cube([ear_t, ear_w, 1]);
    translate([0, 0, -yoke_disk_t - tilt_drop]) rotate([0,90,0]) cylinder(r=ear_w/2, h=ear_t);
  }
  translate([ear_gap + eps, 0, -yoke_disk_t - tilt_drop]) rotate([0,-90,0]) ridge_ring(180/ridge_n);
}
module yoke_use() {
  difference() {
    union() {
      translate([0,0,-yoke_disk_t]) cylinder(d=yoke_disk_d, h=yoke_disk_t);
      yoke_ear(); mirror([1,0,0]) yoke_ear();
    }
    translate([0,0,-50]) cylinder(d=m6_d, h=100);
    translate([0,0,-yoke_disk_t-1]) cylinder(d=(m6_flats+0.4)/cos(30), h=1+m6_head_h+0.5, $fn=6);
    translate([-30, 0, -yoke_disk_t - tilt_drop]) rotate([0,90,0]) cylinder(d=m6_d, h=60);
  }
}
module yoke() { rotate([180,0,0]) yoke_use(); }

// =====================================================================
//  7. cradle (modelled in use: lens face at z=0 looking down, plug edge +Y,
//     charge-port edge -Y).  Exported with the plug edge (+Y) on the bed.
// =====================================================================
module tongue2d() {
  z0 = pt + back_t;
  hull() {
    translate([-pw/2+2, z0 - 0.5]) square([y_top - (-pw/2+2), 1.5]);
    translate([y_top - 1, z0 - 0.5]) square([1, tongue_h + 0.5]);
    translate([0, zk]) circle(r=11);
  }
}
module cradle_use(testfit=false) {
  difference() {
    union() {
      // back plate
      translate([-pl/2 - c_wall, -pw/2 - c_wall, pt]) cube([pl + 2*c_wall, y_top + pw/2 + c_wall, back_t]);
      for (s=[-1,1]) {
        // end walls
        translate([s>0 ? pl/2 : -pl/2 - c_wall, -pw/2 - c_wall, -lip_t])
          cube([c_wall, y_top + pw/2 + c_wall, pt + back_t + lip_t]);
        // lips over the lens face, ends only
        translate([s>0 ? pl/2 - lip_w : -pl/2, -pw/2 - c_wall, -lip_t])
          cube([lip_w + eps, y_top + pw/2 + c_wall, lip_t]);
        // bottom-edge corner stops (charge port / LED / switch stay clear)
        translate([s>0 ? pl/2 - corner_w : -pl/2 - c_wall, -pw/2 - c_wall, -lip_t])
          cube([corner_w + c_wall, c_wall, pt + back_t + lip_t]);
        // keeper-screw bosses
        translate([s*(pl/2 + c_wall - eps), keep_y, pt/2]) rotate([0, s*90, 0]) cylinder(d=6, h=3);
        // crush ribs (friction fit)
        for (z=[3, pt/2, pt-3]) translate([s>0 ? pl/2 - rib_h : -pl/2 - eps, -pw/2, z - 0.6])
          cube([rib_h + eps, y_top + pw/2, 1.2]);
      }
      if (!testfit) {
        rotate([90,0,90]) linear_extrude(tongue_t, center=true) tongue2d();
        for (s=[-1,1]) translate([s*(tongue_t/2 - eps), 0, zk]) rotate([0, s*90, 0]) ridge_ring();
      }
    }
    // keeper screws (M3 x 12, self-tapping into the 2.6 mm pilot)
    for (s=[-1,1]) translate([s*(pl/2 - 1), keep_y, pt/2]) rotate([0, s*90, 0]) cylinder(d=m3_pilot, h=10, $fn=24);
    // notch so a chunky extension-cable socket can't touch the back plate
    translate([-9, pw/2, pt - 1]) cube([18, 10, back_t + 2]);
    if (!testfit) {
      translate([-20, 0, zk]) rotate([0,90,0]) cylinder(d=m6_d, h=40);
      // vents
      for (x=[-25, -19, -13, 13, 19, 25]) translate([x - 1.5, -pw/2 + 4, pt - 1]) cube([3, pw - 8, back_t + 2]);
      // zip-tie anchors (cable strain relief)
      for (s=[-1,1]) for (y=[-pw/2 + 2.5, pw/2 + 1.5]) translate([s*31 - 2.25, y - 1, pt - 1]) cube([4.5, 2, back_t + 2]);
    } else {
      translate([-pl/2 + 8, -pw/2 + 5, pt - 1]) cube([pl - 16, pw - 7, back_t + 2]);
      translate([0, 0, pt + back_t - 0.6]) linear_extrude(1) text("FIT", size=5, halign="center", valign="center");
    }
  }
}
module cradle(testfit=false) { // plug edge (+Y) flat on the bed, lens face vertical
  translate([0, 0, y_top]) rotate([-90,0,0]) cradle_use(testfit);
}

// =====================================================================
//  8. cable clip: snaps on the tube, holds two USB-C cables (print 6)
// =====================================================================
module cable_clip() {
  r = tube_d/2 + 0.1; t = 2.6; cr = 2.7; ct = 1.8;
  linear_extrude(8) difference() {
    union() {
      circle(r=r + t);
      for (a=[150, 210]) rotate(a) translate([r + t + cr, 0]) circle(r=cr + ct);
    }
    circle(r=r);
    polygon([[0,0], [3*r*cos(55), 3*r*sin(55)], [3*r*cos(-55), 3*r*sin(-55)]]); // snap mouth
    for (a=[150, 210]) rotate(a) translate([r + t + cr, 0]) {
      circle(r=cr, $fn=32);
      translate([0, a < 180 ? 0 : -6]) translate([-1.6, 0]) square([3.2, 6]);  // cable slot
    }
  }
}

// =====================================================================
//  9. printed arm segments (alternative to EMT). Octagon prints flat,
//     half-lap joints with a key + 2x M5x20 bolts. Every other segment is
//     flipped over when you assemble, so all of them print lap-side up.
// =====================================================================
module oct_bar(len) {
  translate([0, 0, oct_flats/2]) rotate([0,90,0]) linear_extrude(len) rotate(22.5) circle(d=tube_d, $fn=8);
}
module lap_cut(at_start, len) {   // remove the top half for 'lap' mm at one end
  x0 = at_start ? -1 : len - lap;
  xe = at_start ? 0 : len;            // the free end of this lap
  dir = at_start ? 1 : -1;            // pointing into the bar
  translate([x0, -tube_d, oct_flats/2]) cube([lap + 1, 2*tube_d, tube_d]);
  // groove 28 mm in from the free end
  translate([xe + dir*28, 0, oct_flats/2]) rotate([90,0,0]) linear_extrude(tube_d*2, center=true)
    polygon([[-2.2, 0.01], [2.2, 0.01], [0, -1.7]]);
  // bolts 9 and 41 mm in from the free end; nut trap at the 9 mm one (bed side)
  for (d=[9, 41]) translate([xe + dir*d, 0, -1]) cylinder(d=m5_d, h=tube_d, $fn=24);
  translate([xe + dir*9, 0, -1]) rotate(30) cylinder(d=(m5_flats+0.3)/cos(30), h=1+5.5, $fn=6);
}
module lap_key(at_start, len) {   // V ridge 22 mm in from the free end
  xe = at_start ? 0 : len; dir = at_start ? 1 : -1;
  translate([xe + dir*22, 0, oct_flats/2 - eps]) rotate([90,0,0])
    linear_extrude(oct_flats*0.86, center=true) polygon([[-2, 0], [2, 0], [0, 1.5]]);
}
module arm_segment(len, lap_start, lap_end) {
  difference() {
    union() {
      oct_bar(len);
      if (lap_start) lap_key(true, len);
      if (lap_end) lap_key(false, len);
    }
    if (lap_start) lap_cut(true, len);
    if (lap_end) lap_cut(false, len);
  }
}

// =====================================================================
//  10. phone holder on the upright: split collar + swivel plate, a second
//      yoke (tilt), and a spring grip.  Two steel springs (Century 3CS) in
//      a window in the slider push the moving jaw closed; a printed
//      follower, held by one M3x16, is the fixed spring seat.
// =====================================================================
module jaw2d(zb) {  // jaw cross-section in XZ, inner face at x=0, phone side +X
  polygon([[-jaw_t, zb], [0, zb], [0, pb_z + 6], [lip_in, pb_z + 15],
           [lip_in, pb_z + jaw_h], [-jaw_t, pb_z + jaw_h]]);
}
module xz_extrude(y0, y1) { translate([0, y1, 0]) rotate([90,0,0]) linear_extrude(y1 - y0) children(); }

module phone_tongue() {   // tilt tongue on the back face, axis along X
  translate([x_t - tongue_t/2, 0, 0]) rotate([90,0,90]) linear_extrude(tongue_t) hull() {
    translate([pb_y0, -0.5]) square([25, 1]);
    translate([tilt_y, tilt_z]) circle(r=11);
    translate([pb_y0, tilt_z - 8]) square([1, 8.5]);
  }
  for (s=[-1,1]) translate([x_t + s*(tongue_t/2 - eps), tilt_y, tilt_z]) rotate([0, s*90, 0]) ridge_ring();
}

module phone_body_use() {
  difference() {
    union() {
      translate([pb_xL, pb_y0, 0]) cube([pb_xR - pb_xL, pb_H, pb_z]);
      xz_extrude(pb_y0, pb_y0 + pb_H) jaw2d(pb_z - 1);                         // fixed jaw
      xz_extrude(pb_y0, 0) translate([0, pb_z - 1]) square([ledge, jaw_h - 2]); // bottom ledge
      phone_tongue();
    }
    // spring/slider channel, open at the right end
    translate([pb_xL + 3, ch_yc - ch_w/2, pb_back]) cube([pb_xR - pb_xL, ch_w, ch_h]);
    // follower screw: clearance through the back, pilot into the front wall
    translate([x_p, ch_yc, -1]) cylinder(d=3.4, h=pb_back + 2, $fn=24);
    translate([x_p, ch_yc, pb_back]) cylinder(d=m3_pilot, h=ch_h + pb_front - 0.6, $fn=24);
    translate([x_p, ch_yc, -eps]) cylinder(d1=4.4, d2=3.4, h=0.5, $fn=24);
    // relief for the moving jaw's ledge chamfer when closed
    translate([pb_xR - ledge - 0.6, pb_y0 - 1, pb_z - ledge - 0.6]) cube([ledge + 1, 1 + 3.5, ledge + 1]);
    // cable notches (USB-C plug / cable can pass the grip's bottom edge)
    for (x=[15, 48]) translate([x - 3.5, pb_y0 - 1, -1]) cube([7, 6, pb_z + jaw_h + 2]);
    // tilt bolt
    translate([x_t - 20, tilt_y, tilt_z]) rotate([0,90,0]) linear_extrude(40) teardrop2d(m6_d);  // tip +Y (up when printed)
    // label on the back face
    translate([(pb_xL + x_t - tongue_t/2)/2 - 2, ch_yc, 0.6]) mirror([0,0,1])
      linear_extrude(1) mirror([1,0,0]) text("HOT SPOTTER", size=4.2, halign="center", valign="center");
  }
}
module phone_body() { translate([0, 0, -pb_y0]) rotate([90,0,0]) phone_body_use(); }  // bottom edge on the bed

// moving part: slider + jaw, modelled at opening G (jaw face at x=G)
module phone_slider_use(G=G_closed) {
  x0 = G - Ls_face;
  difference() {
    union() {
      translate([x0, ch_yc - sl_w/2, zs0]) cube([Ls_face + eps, sl_w, sl_h]);
      translate([G, 0, 0]) mirror([1,0,0]) xz_extrude(pb_y0, pb_y0 + pb_H) jaw2d(zs0);
      // bottom ledge with a 45 deg chamfer underneath (prints without support)
      xz_extrude(pb_y0, 0) polygon([[G, pb_z + 0.3 - ledge], [G, pb_z + jaw_h - 2],
                                    [G - ledge, pb_z + jaw_h - 2], [G - ledge, pb_z + 0.3]]);
    }
    translate([x0 + w0, ch_yc - win_w/2, zs0 - 1]) cube([w1 - w0, win_w, sl_h + 2]);
    translate([G + jaw_t/2, 20, pb_z + jaw_h - 0.6]) linear_extrude(1)
      rotate(90) text("PUSH", size=3.6, halign="center", valign="center");
  }
}
module phone_slider() { translate([Ls_face - G_closed, 0, -zs0]) phone_slider_use(); }

module phone_follower() {  // print flat
  difference() {
    translate([-f_len/2, -(win_w - 0.4)/2, 0]) cube([f_len, win_w - 0.4, sl_h - 0.3]);
    translate([0, 0, -1]) cylinder(d=3.4, h=sl_h + 2, $fn=24);
  }
}
module phone_spacer() {    // range spacer: IN for 58-80 mm phones, OUT for 78-100 mm
  difference() {
    translate([0, -(win_w - 0.4)/2, 0]) cube([spacer_len, win_w - 0.4, sl_h - 0.3]);
    translate([spacer_len/2, 0, sl_h - 0.3 - 0.5]) linear_extrude(1)
      text(str(phone_w_min, "-", phone_w_min + travel), size=3.4, halign="center", valign="center");
  }
}

// collar: tube along +Z, slit + pinch ears on -X, swivel plate on +X
module hex_prism_y(flats, h) { rotate([-90,0,0]) rotate(30) cylinder(d=flats/cos(30), h=h, $fn=6); } // vertex up
module phone_collar() {
  r = sock_bore/2; cr = 2.7; ct = 1.8; ex = col_Ro + 7.5;
  difference() {
    union() {
      cylinder(r=col_Ro, h=col_H, $fn=96);
      translate([-(col_Ro + 15), -(1 + col_ear), 0]) cube([19, 2*(1 + col_ear), col_H]);
      xz_extrude(-plate_t/2, plate_t/2) polygon([[col_Ro - 3, 0], [col_px + 8, 0], [col_px + 17, 9],
                                                [col_px + 17, col_H - 9], [col_px + 8, col_H], [col_Ro - 3, col_H]]);
      // cable hooks (same channel as the cable clip)
      for (a=[90, 270]) rotate(a) translate([col_Ro + cr - 0.5, 0, 0]) cylinder(r=cr + ct, h=col_H, $fn=32);
    }
    translate([0,0,-1]) cylinder(r=r, h=col_H + 2, $fn=96);
    for (z=[0, col_H]) translate([0,0,z]) mirror([0,0,z > 0 ? 1 : 0]) translate([0,0,-eps])
      cylinder(r1=r + 1.2, r2=r, h=1.2, $fn=96);                                 // lead-ins
    translate([-(col_Ro + 16), -1, -1]) cube([col_Ro + 16, 2, col_H + 2]);       // slit
    // pinch bolt M6x30: head in a hex pocket on +Y, wing nut on -Y
    translate([-ex, 0, col_H/2]) teardrop_y(m6_d, -20, 20);
    translate([-ex, 1 + col_ear - 4.5, col_H/2]) hex_prism_y(m6_flats + 0.4, 6);
    // swivel bolt M6x30
    translate([col_px, 0, col_H/2]) teardrop_y(m6_d, -10, 10);
    for (a=[90, 270]) rotate(a) translate([col_Ro + cr - 0.5, 0, 0]) {
      translate([0,0,-1]) cylinder(r=cr, h=col_H + 2, $fn=32);
      translate([0, a < 180 ? 0 : -6, -1]) translate([-1.6, 0]) cube([3.2, 6, col_H + 2]);
    }
  }
}

// =====================================================================
//  11. base pad (OPTIONAL, instead of the clamp): screws down onto the
//      bench top.  Same tube socket and M5 pinch bolts as the clamp, and
//      the tube bottoms 14 mm (= top_t) above the bench, so the post,
//      elbow, boom and camera height are unchanged.  Print flange-down.
//      Use frame = print frame: bench top z=0, post axis at x=y=0,
//      pinch ears/slit toward -Y.
// =====================================================================
pad_bore_from = top_t - pad_t;          // bore floor at z = top_t, same as on the clamp
module pad_footprint() { offset(r=pad_r) offset(delta=-pad_r) square(pad_W, center=true); }
module pad_root_fillet() {              // concave quarter-round, socket -> flange
  n = 8;
  for (k=[0:n-1]) hull() for (j=[k, k+1]) {
    z = pad_fil*j/n; w = pad_fil - sqrt(pad_fil*pad_fil - (pad_fil - z)*(pad_fil - z));
    translate([0, 0, pad_t + z - eps]) linear_extrude(0.02)
      offset(delta=w) translate([-sock_W/2, -(sock_H - sock_zc)]) square([sock_W, sock_H]);
  }
}
// rib along +X from a socket face at x=x0 out to x=x1, constant thickness.  Its side
// profile is the triangle closed with radius pad_rho against the socket face and the
// flange, so the rib blends in tangentially at both ends (no sharp notch at the top).
module pad_gusset(x0, x1, h) {
  tl = pad_rho*tan(atan((x1 - x0)/h)/2);   // where the top blend meets the socket face
  rotate([90,0,0]) linear_extrude(pad_gus_t, center=true) intersection() {
    offset(r=-pad_rho, $fn=48) offset(delta=pad_rho) union() {
      polygon([[x0 - 1, pad_t], [x1, pad_t], [x0 - 1, pad_t + h]]);
      translate([x0 - 30, pad_t - 30]) square([30, h + tl + 30]);
      translate([x0 - 30, pad_t - 30]) square([x1 - x0 + 60, 30]);
    }
    union() {
      translate([x0 - 0.5, pad_t - eps]) square([x1 - x0 + 1, h + 30]);
      translate([x0 - 8, pad_t - eps]) square([7.6, h]);   // buried in the socket
    }
  }
}
// corner rib from the socket-block corner c straight to the screw boss s
module pad_corner_rib(c, s, h) {
  d = (s - c)/norm(s - c);
  c = c - 3*d;                          // start 3 mm inside the block corner (avoids coincident edges)
  L = norm(s - c);
  translate([c[0], c[1], 0]) rotate(atan2(s[1] - c[1], s[0] - c[0]))
    pad_gusset(0, L - pad_csk/2 - 2.5, h);
}
module base_pad() {
  r_g = pad_W/2 - 7;                    // axial rib outer end
  difference() {
    union() {
      // flange with a 0.6 mm chamfer on the bed edge (elephant's foot)
      hull() {
        translate([0,0,0.6]) linear_extrude(pad_t - 0.6) pad_footprint();
        linear_extrude(0.6) offset(delta=-0.6) pad_footprint();
      }
      // socket block, standing up: block +Y -> +Z, block +Z (ears) -> -Y.
      // Same bore, slit, M5 pinch bolts and nut traps as the clamp's socket.
      translate([0, sock_zc, pad_t]) rotate([90,0,0])
        socket_block(pad_sock_L, pad_bore_from, pad_slit, true);
      pad_root_fillet();
      // 4 axial ribs (+-X = bolt-head / nut sides, +Y back, -Y ear side below the slit)
      pad_gusset(sock_W/2, r_g, pad_gus_h[0]);
      mirror([1,0,0]) pad_gusset(sock_W/2, r_g, pad_gus_h[0]);
      rotate(90)  pad_gusset(sock_zc, r_g, pad_gus_h[1]);
      rotate(-90) pad_gusset(sock_H - sock_zc, r_g, pad_gus_h[2]);
      // 4 corner ribs, socket corner -> screw boss
      for (sx=[-1,1]) {
        pad_corner_rib([sx*sock_W/2, sock_zc], [sx*pad_screw, pad_screw], pad_gus_h[3]);
        pad_corner_rib([sx*sock_W/2, -(sock_H - sock_zc)], [sx*pad_screw, -pad_screw], pad_gus_h[4]);
      }
    }
    // round relief at the bottom of the pinch slit (no sharp notch)
    translate([0, 1, pad_t + pad_slit]) rotate([90,0,0]) cylinder(d=pad_relief, h=sock_H - sock_zc + 2, $fn=32);
    // countersunk + counterbored screw holes (90 deg; #8/#10 flat-head wood screws or M5 DIN 7991)
    for (sx=[-1,1]) for (sy=[-1,1]) translate([sx*pad_screw, sy*pad_screw, 0]) {
      zs = pad_t - pad_cbore;            // top of the countersink = head seat
      translate([0,0,-1]) cylinder(d=pad_hole, h=pad_t + 2, $fn=32);
      translate([0,0,zs - (pad_csk - pad_hole)/2]) cylinder(d1=pad_hole, d2=pad_csk, h=(pad_csk - pad_hole)/2 + eps, $fn=48);
      translate([0,0,zs]) cylinder(d=pad_csk + 0.5, h=pad_cbore + 1, $fn=48);
    }
    // label on the flange top, between the +X rib and the +X+Y corner rib
    if (pad_label) translate([48, 13, pad_t - 0.6]) linear_extrude(1)
      text("HOT SPOTTER", size=3.5, halign="center", valign="center");
  }
}
// screws (illustrative): flat-head, 25 mm
module pad_screws(len=25.4) {   // #10 x 1"
  for (sx=[-1,1]) for (sy=[-1,1]) color([0.6,0.6,0.62]) translate([sx*pad_screw, sy*pad_screw, 0]) {
    translate([0,0,pad_t - pad_cbore - (pad_csk - pad_hole)/2 - 0.3]) cylinder(d1=4.8, d2=pad_csk - 0.6, h=(pad_csk - pad_hole)/2);
    translate([0,0,pad_t - pad_cbore - len]) cylinder(d=4.4, h=len - 2);
  }
}
module pad_bench_mock(w=260, d=260, t=19) {
  color([0.78,0.66,0.48], 0.9) translate([-w/2, -d/2, -t]) cube([w, d, t]);
}
module pad_view() {
  pad_bench_mock();
  color("orange") base_pad();
  pad_screws();
  translate([0, 0, top_t]) tube(170);
}
// 2D section of children at plane x = c, drawn in the (y, z) plane, extruded thin
module pad_cut(c) {
  linear_extrude(1) mirror([1,-1,0]) projection(cut=true)
    rotate([0,90,0]) translate([-c,0,0]) children();
}
module pad_section() {   // left: cut through the bore (x=3); right: cut through two screws (x=pad_screw)
  translate([-115, -40, 0]) for (k=[0,1]) translate([k*230, 0, 0]) {   // laid out in the XY plane: view from top
    c = k == 0 ? 3 : pad_screw;           // x=3: just beside the pinch slit, through the bore
    translate([0,0,-0.5]) color([0.78,0.66,0.48]) pad_cut(c) pad_bench_mock(200, 200);
    color("orange") pad_cut(c) base_pad();
    translate([0,0,0.5]) color([0.55,0.55,0.6]) pad_cut(c) pad_screws();
    if (k == 0) translate([0,0,0.5]) color([0.45,0.47,0.5]) pad_cut(c) translate([0,0,top_t]) tube(110);
  }
}

// ----- preview helpers ----------------------------------------------
module spring_mock(L, d=spr_od, wire=0.58) {  // along +X
  n = 12; p = L/n;
  color([0.75,0.75,0.78]) for (i=[0:n-1]) translate([i*p + p/2, 0, 0]) rotate([0, 90, 0])
    rotate_extrude($fn=16) translate([d/2 - wire/2, 0]) circle(d=wire*1.6, $fn=8);
}
module phone_mock(W, H=155, T=10) {
  color([0.12,0.12,0.14]) translate([rubber_t, 0, pb_z]) hull() for (x=[3, W-3]) for (y=[3, H-3])
    translate([x, y, 0]) cylinder(r=3, h=T, $fn=24);
  color([0.25,0.35,0.55]) translate([rubber_t + 4, 8, pb_z + T - 0.2]) cube([W - 8, H - 16, 0.4]);
  color([0.15,0.15,0.15]) for (x=[0, W + rubber_t]) translate([x, pb_y0 + 3, pb_z + 0.5]) cube([rubber_t, 30, 7]); // rubber pads
  color("silver") translate([rubber_t + W/2 - 4, -6.5, pb_z + T/2 - 1.5]) cube([8, 6.5, 3]);   // USB-C plug
}
// grip in use: body, slider at opening G, springs + follower (+ spacer)
module phone_grip(W=75, spacer=true, xray=false) {
  G = W + 2*rubber_t;
  L = stack_len(G, spacer);
  x0 = G - Ls_face;
  color("orange") difference() {
    phone_body_use();
    if (xray) for (xs=[[pb_xL - 1, -jaw_t - 0.5], [0.5, 200]])   // cut away the front wall, keep the fixed jaw
      translate([xs[0], ch_yc - ch_w/2 - 2, pb_back + ch_h/2]) cube([xs[1] - xs[0], ch_w + 4, pb_z]);
  }
  color([1,0.55,0.1]) phone_slider_use(G);
  color("gold") translate([x_p, ch_yc, zs0]) phone_follower();
  if (spacer) color([0.2,0.6,1]) translate([x0 + w0, ch_yc, zs0]) phone_spacer();
  translate([x0 + w0 + (spacer ? spacer_len : 0), ch_yc, zs0 + sl_h/2]) {
    spring_mock(L/2); translate([L/2, 0, 0]) spring_mock(L/2);
  }
  color([0.4,0.4,0.42]) translate([x_p, ch_yc, -2.4]) cylinder(d=5.5, h=2.4, $fn=24);  // M3 pan head
}
// collar frame -> grip frame. Grip X -> -X, grip Y -> +Z, grip Z -> +Y
module at_grip_on_collar() {
  multmatrix([[-1,0,0, col_px + x_t], [0,0,1, plate_t/2 + yoke_disk_t + tilt_drop - tilt_z],
              [0,1,0, col_H/2 - tilt_y], [0,0,0,1]]) children();
}
module phone_holder_parts(W=75, spacer=true, phone=true, xray=false, side=1) {
  // side=-1: yoke on the other face of the swivel plate (phone faces -Y)
  color("orange") phone_collar();
  translate([col_px, 0, 0]) rotate(side > 0 ? 0 : 180) translate([-col_px, 0, 0]) {
    color("orange") translate([col_px, plate_t/2, col_H/2]) rotate([90,0,0]) yoke_use();
    color([0.6,0.6,0.62]) translate([col_px, -plate_t/2, col_H/2]) rotate([90,0,0]) cylinder(d=12, h=6); // wing nut
    at_grip_on_collar() {
      phone_grip(W, spacer, xray);
      if (phone) phone_mock(W);
    }
  }
}
module phone_view() {
  translate([0,0,-40]) tube(140);
  phone_holder_parts(75, true);
}
module phone_section_view() {
  phone_grip(75, true, true);
  translate([0, -70, 0]) phone_grip(92, false, true);
  translate([0, 60, 0]) {   // loose parts
    color("gold") translate([-30, 0, 0]) phone_follower();
    color([0.2,0.6,1]) translate([-10, 0, 0]) phone_spacer();
    translate([20, 0, sl_h/2]) spring_mock(spr_free);
  }
}

// =====================================================================
//  assembly (use position).  Bench top at z=0, front face of the bench at
//  x=0, bench extends toward +X.
// =====================================================================
module bench_mock(w=440, d=420) {
  color("silver", 0.9) {
    translate([0, -w/2, -1.5]) cube([d, w, 1.5]);                          // top
    translate([0, -w/2, -bench_thickness]) cube([1.5, w, bench_thickness]); // apron
    translate([0, -w/2, -bench_thickness]) cube([lip_depth, w, 1.5]);      // bottom return flange
  }
  color([0.15,0.15,0.15]) translate([lip_depth + 1, -w/2, -bench_thickness - 160]) cube([1.5, w, 160 - 2]); // perforated panel
}
module camera_mock() {
  color([0.12,0.12,0.13]) translate([-cam_L/2, -cam_W/2, 0]) cube([cam_L, cam_W, cam_T]);
  color("silver") translate([-cam_L/2 + 16, -3, -0.4]) cylinder(d=9, h=0.5);
  color([0.6,0.6,0.6]) translate([-cam_L/2 + 16, 3, -0.4]) cylinder(d=5, h=0.5);
  color("silver") translate([-4, cam_W/2, 4]) cube([8, 6.5, 2.6]);                 // USB-C plug
  color([0.1,0.4,0.9]) translate([-6, cam_W/2 + 6.5, 1]) cube([12, 16, 8]);          // extension socket
  color("black") translate([4, -cam_W/2 - 14, 4]) cube([10, 14, 6]);                 // charge plug
}
module tube(L) { color([0.75,0.77,0.8]) cylinder(d=tube_d, h=L); }

module clamp_parts() {
  color("orange") translate([0, sock_W/2, 0]) rotate([90,0,0]) clamp_body();
  pad_top = -bench_thickness - rubber_t;
  color("orange") translate([screw_x, 0, pad_top]) rotate([180,0,0]) clamp_pad();
  tip = pad_top - pad_h + 3;            // bolt tip inside the acorn nut
  hb  = tip - m8_len;                   // underside of the bolt head
  color([0.6,0.6,0.62]) translate([screw_x, 0, hb]) cylinder(d=8, h=m8_len);
  color("orange") translate([screw_x, 0, hb + knob_h]) rotate([180,0,0]) clamp_knob(); // head pocket faces down
  color([0.6,0.6,0.62]) translate([screw_x, 0, hb + knob_h]) cylinder(d=m8_flats/cos(30), h=m8_nut_h, $fn=6); // lock nut
}
module clamp_view() {
  bench_mock(110, 120);
  clamp_parts();
  translate([sock_x, 0, top_t]) tube(90);
}
module assembly(printed=false, pad=false) {
  if (!pad) { bench_mock(); clamp_parts(); }
  else {
    color([0.78,0.66,0.48], 0.9) translate([sock_x - 110, -220, -19]) cube([480, 440, 19]);
    translate([sock_x, 0, 0]) { color("orange") base_pad(); pad_screws(); }
  }
  // post + boom
  if (!printed) {
    translate([sock_x, 0, top_t]) tube(post_len);
    translate([sock_x + elbow_stop, 0, boom_z]) rotate([0,90,0]) tube(boom_len);
  } else {
    chain(post_len, [sock_x, 0, top_t], false);
    chain(boom_len, [sock_x + elbow_stop, 0, boom_z], true);
  }
  color("orange") translate([sock_x, 0, boom_z]) rotate([-90,0,0]) translate([0,0,-sock_zc]) elbow();
  color("orange") translate([sock_x + elbow_stop + boom_len - (sock_L - 3), 0, boom_z - sock_zc])
    rotate([0,0,-90]) boom_head();
  color("orange") translate([pan_x, 0, boom_z - sock_zc]) yoke_use();
  translate([pan_x, 0, boom_z - sock_zc - yoke_disk_t - tilt_drop - zk]) {
    color("orange") cradle_use();
    camera_mock();
  }
  // phone holder on the upright
  translate([sock_x, 0, top_t + phone_on_post]) rotate(phone_yaw) phone_holder_parts(78, true, side=-1);
  // clips + cables (illustrative)
  for (z=[top_t + 120, top_t + 330, top_t + 540])
    color("gold") translate([sock_x, 0, z]) rotate([0,0,180]) cable_clip();
  for (x=[sock_x + 90, sock_x + 200])
    color("gold") translate([x, 0, boom_z]) rotate([0,-90,0]) rotate([0,0,90]) cable_clip();
  color([0.1,0.4,0.9]) cable_path([[pan_x, 30, cam_height+6], [pan_x-30, 18, boom_z-10], [sock_x+40, 18, boom_z+8],
                                   [sock_x+8, 17, boom_z-40], [sock_x, 19.2, top_t+phone_on_post+col_H+5], [sock_x, 19.2, top_t+phone_on_post-5], [sock_x-8, 17, top_t+60], [-40, 17, -60]]);
  color("black") cable_path([[pan_x+8, -36, cam_height+6], [pan_x-30, -18, boom_z-10], [sock_x+40, -18, boom_z+8],
                             [sock_x+8, -17, boom_z-40], [sock_x, -19.2, top_t+phone_on_post+col_H+5], [sock_x, -19.2, top_t+phone_on_post-5], [sock_x-8, -17, top_t+60], [-40, -17, -60]]);
}
module cable_path(pts) { for (i=[0:len(pts)-2]) hull() { translate(pts[i]) sphere(d=4, $fn=12); translate(pts[i+1]) sphere(d=4, $fn=12); } }

// segment chain for the printed variant; start point = tube end in socket
module chain(L, p0, horizontal) {
  n = nseg(L); s = seglen(L);
  for (k=[0:n-1]) {
    off = k*(s - lap);
    flipk = (k % 2 == 1);
    translate(p0) rotate(horizontal ? [0,0,0] : [0,-90,0]) translate([off, 0, 0])
      color(k % 2 ? [0.95,0.55,0.1] : [1,0.65,0.2]) seg_k(k, n, s, flipk);
  }
}
module seg_k(k, n, s, flipk) {
  // centre the bar on its axis, flip every other one about its own axis
  if (k == 0) { rotate([0,0,0]) translate([0,0,-oct_flats/2]) arm_segment(s, false, true); }
  else if (k == n-1) {
    // end piece reversed (lap at the start); lap must face opposite to the previous piece
    rotate([(k % 2 == 1) ? 0 : 180, 0, 0]) translate([s, 0, 0]) rotate([0,0,180]) rotate([180,0,0])
      translate([0,0,-oct_flats/2]) arm_segment(s, false, true);
  }
  else rotate([flipk ? 180 : 0, 0, 0]) translate([0,0,-oct_flats/2]) arm_segment(s, true, true);
}

module cradle_view() {
  translate([0, 0, 0]) { color("orange") cradle_use(); camera_mock(); }
  color("orange") translate([0, 0, zk + yoke_disk_t + tilt_drop]) yoke_use();
  color("orange", 0.9) translate([110, 0, 0]) cradle_use(true);
  translate([110, 0, 0]) camera_mock();
}

// =====================================================================
if (part == "assembly")          assembly(false);
if (part == "assembly_printed")  assembly(true);
if (part == "cradle_view")       cradle_view();
if (part == "clamp_view")        clamp_view();
if (part == "clamp_body")        clamp_body();
if (part == "clamp_knob")        clamp_knob();
if (part == "clamp_pad")         clamp_pad();
if (part == "elbow")             elbow();
if (part == "boom_head")         boom_head();
if (part == "yoke")              yoke();
if (part == "cradle")            cradle(false);
if (part == "cradle_testfit")    cradle(true);
if (part == "cable_clip")        cable_clip();
if (part == "arm_post_end")      arm_segment(seglen(post_len), false, true);
if (part == "arm_post_mid")      arm_segment(seglen(post_len), true, true);
if (part == "arm_boom_end")      arm_segment(seglen(boom_len), false, true);
if (part == "phone_view")        phone_view();
if (part == "phone_section")     phone_section_view();
if (part == "phone_collar")      phone_collar();
if (part == "phone_body")        phone_body();
if (part == "phone_slider")      phone_slider();
if (part == "phone_follower")    phone_follower();
if (part == "phone_spacer")      phone_spacer();
if (part == "base_pad")          base_pad();
if (part == "pad_view")          pad_view();
if (part == "pad_section")       pad_section();
if (part == "assembly_pad")      assembly(false, true);
