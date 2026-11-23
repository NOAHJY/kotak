/* robot_logic.h -- logika murni (tanpa HAL) untuk perintah dari aplikasi Android.
 *
 * Jalur data: HP --WiFi/TCP--> ESP8266/ESP32 --UART--> STM32 USART1
 *
 * Format satu baris (tanpa '\n'):   X,Y,ARAH,PUTAR,KECEPATAN,GRIP,LIFT
 *   contoh:  0,60,FORWARD,NONE,60,NONE,NONE
 *   X, Y      nilai joystick gerak -100..100 (X kanan +, Y maju +)
 *   ARAH      STOP FORWARD BACKWARD LEFT RIGHT FORWARD-LEFT FORWARD-RIGHT BACKWARD-LEFT BACKWARD-RIGHT
 *             (hanya informasi/validasi; gerak dihitung dari X, Y)
 *   PUTAR     NONE CLOCKWISE ANTICLOCKWISE
 *   KECEPATAN 0..100 = akar(X^2 + Y^2)
 *   GRIP      NONE CLOSE OPEN
 *   LIFT      NONE UP DOWN
 */
#ifndef ROBOT_LOGIC_H
#define ROBOT_LOGIC_H

#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

typedef struct {
    int vx;    /* geser kanan(+)            -100..100 */
    int vy;    /* maju(+)                   -100..100 */
    int w;     /* putar searah jarum jam(+) -100..100 */
    int grip;  /* +1 tutup, -1 buka, 0 diam */
    int lift;  /* +1 naik,  -1 turun, 0 diam */
} RobotCmd;

static int rb_clamp(int v, int lo, int hi)
{
    return v < lo ? lo : (v > hi ? hi : v);
}

static const char *const RB_DIR_NAMES[] = {
    "STOP", "FORWARD", "BACKWARD", "LEFT", "RIGHT",
    "FORWARD-LEFT", "FORWARD-RIGHT", "BACKWARD-LEFT", "BACKWARD-RIGHT"
};

/* Return 1 jika baris valid. */
static int robot_parse(const char *s, RobotCmd *c)
{
    char buf[64];
    char *t[7];
    char *p, *end;
    int n = 0, i, found = 0, x, y, spd;
    float mag;
    size_t len = strlen(s);

    if (len == 0 || len >= sizeof buf) return 0;
    memcpy(buf, s, len + 1);

    t[n++] = buf;
    for (p = buf; *p && n < 7; p++) {
        if (*p == ',') { *p = 0; t[n++] = p + 1; }
    }
    if (n != 7 || strchr(t[6], ',') != NULL) return 0;

    /* X, Y */
    x = (int)strtol(t[0], &end, 10);
    if (end == t[0] || *end != 0) return 0;
    y = (int)strtol(t[1], &end, 10);
    if (end == t[1] || *end != 0) return 0;
    x = rb_clamp(x, -100, 100);
    y = rb_clamp(y, -100, 100);

    /* ARAH (hanya divalidasi) */
    for (i = 0; i < (int)(sizeof RB_DIR_NAMES / sizeof RB_DIR_NAMES[0]); i++) {
        if (strcmp(t[2], RB_DIR_NAMES[i]) == 0) { found = 1; break; }
    }
    if (!found) return 0;

    /* PUTAR */
    /* KECEPATAN */
    spd = (int)strtol(t[4], &end, 10);
    if (end == t[4] || *end != 0) return 0;
    spd = rb_clamp(spd, 0, 100);

    if (strcmp(t[3], "NONE") == 0) c->w = 0;
    else if (strcmp(t[3], "CLOCKWISE") == 0) c->w = spd;
    else if (strcmp(t[3], "ANTICLOCKWISE") == 0) c->w = -spd;
    else return 0;

    if (strcmp(t[5], "NONE") == 0) c->grip = 0;
    else if (strcmp(t[5], "CLOSE") == 0) c->grip = 1;
    else if (strcmp(t[5], "OPEN") == 0) c->grip = -1;
    else return 0;

    if (strcmp(t[6], "NONE") == 0) c->lift = 0;
    else if (strcmp(t[6], "UP") == 0) c->lift = 1;
    else if (strcmp(t[6], "DOWN") == 0) c->lift = -1;
    else return 0;

    /* Gerak: arah dari (X, Y), besar dari KECEPATAN = akar(X^2 + Y^2) */
    mag = sqrtf((float)(x * x + y * y));
    if (mag > 0.0f && strcmp(t[2], "STOP") != 0) {
        c->vx = (int)lroundf((float)x / mag * (float)spd);
        c->vy = (int)lroundf((float)y / mag * (float)spd);
    } else {
        c->vx = 0;
        c->vy = 0;
    }
    return 1;
}

/* Jika ada nilai > 100, semua diskala agar perbandingan tetap. */
static void rb_scale(const float *m, int n, int *out)
{
    float mx = 100.0f;
    int i;
    for (i = 0; i < n; i++)
        if (fabsf(m[i]) > mx) mx = fabsf(m[i]);
    for (i = 0; i < n; i++)
        out[i] = (int)lroundf(m[i] * 100.0f / mx);
}

/* PROJECT_KOTAK (STM32F405): 3 roda omni, sesuai gambar (Springer, "Intuitive Control of Three
 * Omni-Wheel-Based Mobile Robot"):
 *      F  = roda depan (di atas, penggerak mendatar -> geser kiri/kanan)
 *      L  = roda belakang kiri,  R = roda belakang kanan
 * Posisi roda mengelilingi pusat: F 90 derajat, L 210 derajat, R 330 derajat.
 * Arah positif tiap roda = searah putaran berlawanan jarum jam (CCW) mengelilingi robot:
 *      F =       -vx            + wc
 *      L =  0.5*vx - 0.866*vy   + wc
 *      R =  0.5*vx + 0.866*vy   + wc
 * wc = putaran CCW = -w (aplikasi: w positif = searah jarum jam).
 * Cocok dengan gambar: maju -> F=0, L dan R berlawanan; maju-kiri -> R=0; maju-kanan -> L=0.
 * Jika roda berputar terbalik, ubah INV_* di robot_app.c (atau tukar kabel motor). */
static void robot_mix_omni3(const RobotCmd *c, int *oL, int *oF, int *oR)
{
    const float k = 0.8660254f;       /* sin(60) */
    float vx = (float)c->vx;
    float vy = (float)c->vy;
    float wc = -(float)c->w;
    float m[3];
    int o[3];
    m[0] = 0.5f * vx - k * vy + wc;   /* L */
    m[1] = -vx + wc;                  /* F */
    m[2] = 0.5f * vx + k * vy + wc;   /* R */
    rb_scale(m, 3, o);
    *oL = o[0];
    *oF = o[1];
    *oR = o[2];
}

/* PROJECT_KOTAK2 (STM32F401): 2 roda -> out[0]=kiri (TIM3), out[1]=kanan (TIM4). vx diabaikan. */
static void robot_mix_diff2(const RobotCmd *c, int out[2])
{
    float m[2];
    m[0] = (float)(c->vy + c->w);
    m[1] = (float)(c->vy - c->w);
    rb_scale(m, 2, out);
}

#endif
