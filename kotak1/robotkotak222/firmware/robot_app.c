/* robot_app.c -- penerima frame dari ESP8266/ESP32 lewat UART (USART1) dan penggerak motor.
 *
 * Jalur: HP --WiFi/TCP--> ESP --UART--> STM32 (PA10 = RX, PA9 = TX).
 * Frame: X,Y,ARAH,PUTAR,KECEPATAN,GRIP,LIFT  (lihat robot_logic.h)
 *
 * Cara pakai di project CubeMX/CubeIDE:
 *   1. Salin robot_logic.h dan robot_app.c ke Core/Src (atau Core/Inc untuk .h).
 *   2. Di main.c:
 *        USER CODE BEGIN Includes : #include "robot_app.h" (atau deklarasikan prototipe di bawah)
 *        USER CODE BEGIN 2        : robot_init();
 *        di while(1)              : robot_poll();
 *   3. Untuk PROJECT_KOTAK2 (STM32F401) tambahkan define:  ROBOT_KOTAK2
 *
 * WAJIB di CubeMX:
 *   - USART1 -> NVIC Settings -> centang "USART1 global interrupt"
 *   - Isi Prescaler & Period untuk TIM yang dipakai (mis. Prescaler 35/36, Period 99)
 *   - Baud rate USART1 = 115200 (harus sama dengan Serial.begin di program ESP), 8N1
 */
#include "main.h"
#include "robot_logic.h"
#include <stdlib.h>
#include <string.h>

extern UART_HandleTypeDef huart1;
#ifdef ROBOT_KOTAK2
extern TIM_HandleTypeDef htim3, htim4;
#else
extern TIM_HandleTypeDef htim2, htim3, htim4;
#endif

#define FAILSAFE_MS 300u

/* Penempatan roda (gambar acuan: F di atas, L kiri-belakang, R kanan-belakang).
 * Ubah di sini jika kabel/timer berbeda. */
#define BTS_LEFT    htim2   /* roda L (kiri)  */
#define BTS_FRONT   htim3   /* roda F (depan) */
#define BTS_RIGHT   htim4   /* roda R (kanan) */

/* Arah putar motor: ubah ke -1 jika roda berputar terbalik. */
#define INV_LEFT    1
#define INV_FRONT   1
#define INV_RIGHT   1

static uint8_t rx_byte;
static char line[64];
static uint8_t idx = 0;
static char ready[64];
static volatile uint8_t line_ready = 0;
static uint32_t last_rx_ms = 0;
static uint8_t stopped = 1;

/* s = -100..100 -> RPWM (CH1) bila positif, LPWM (CH2) bila negatif */
static void bts_set(TIM_HandleTypeDef *t, int s)
{
    uint32_t duty = (uint32_t)abs(s) * (__HAL_TIM_GET_AUTORELOAD(t) + 1u) / 100u;
    if (s >= 0) {
        __HAL_TIM_SET_COMPARE(t, TIM_CHANNEL_1, duty);
        __HAL_TIM_SET_COMPARE(t, TIM_CHANNEL_2, 0);
    } else {
        __HAL_TIM_SET_COMPARE(t, TIM_CHANNEL_1, 0);
        __HAL_TIM_SET_COMPARE(t, TIM_CHANNEL_2, duty);
    }
}

static void dc_set(GPIO_TypeDef *pcw, uint16_t cw, GPIO_TypeDef *pccw, uint16_t ccw, int dir)
{
    HAL_GPIO_WritePin(pcw,  cw,  dir > 0 ? GPIO_PIN_SET : GPIO_PIN_RESET);
    HAL_GPIO_WritePin(pccw, ccw, dir < 0 ? GPIO_PIN_SET : GPIO_PIN_RESET);
}

static void robot_apply(const RobotCmd *c)
{
#ifdef ROBOT_KOTAK2
    int m[2];
    robot_mix_diff2(c, m);
    bts_set(&htim3, m[0]);
    bts_set(&htim4, m[1]);
#else
    int mL, mF, mR;
    robot_mix_omni3(c, &mL, &mF, &mR);
    bts_set(&BTS_LEFT,  INV_LEFT  * mL);
    bts_set(&BTS_FRONT, INV_FRONT * mF);
    bts_set(&BTS_RIGHT, INV_RIGHT * mR);
#endif
    dc_set(DC1_CW_GPIO_Port, DC1_CW_Pin, DC1_CCW_GPIO_Port, DC1_CCW_Pin, c->grip); /* DC1 = grip */
    dc_set(DC2_CW_GPIO_Port, DC2_CW_Pin, DC2_CCW_GPIO_Port, DC2_CCW_Pin, c->lift); /* DC2 = lift */
    /* DC3 (hanya ada di KOTAK) sengaja tidak dipakai: batas rules maks 5 motor. */
}

static void robot_stop(void)
{
    RobotCmd z = {0, 0, 0, 0, 0};
    robot_apply(&z);
    stopped = 1;
}

void robot_init(void)
{
#ifndef ROBOT_KOTAK2
    HAL_TIM_PWM_Start(&htim2, TIM_CHANNEL_1);
    HAL_TIM_PWM_Start(&htim2, TIM_CHANNEL_2);
#endif
    HAL_TIM_PWM_Start(&htim3, TIM_CHANNEL_1);
    HAL_TIM_PWM_Start(&htim3, TIM_CHANNEL_2);
    HAL_TIM_PWM_Start(&htim4, TIM_CHANNEL_1);
    HAL_TIM_PWM_Start(&htim4, TIM_CHANNEL_2);
    robot_stop();
    last_rx_ms = HAL_GetTick();
    HAL_UART_Receive_IT(&huart1, &rx_byte, 1);
}

/* Panggil terus-menerus di while(1). */
void robot_poll(void)
{
    if (line_ready) {
        char buf[64];
        RobotCmd c;
        __disable_irq();
        memcpy(buf, ready, sizeof buf);
        line_ready = 0;
        __enable_irq();
        if (robot_parse(buf, &c)) {
            robot_apply(&c);
            stopped = 0;
            last_rx_ms = HAL_GetTick();
        }
    }
    /* Failsafe: tidak ada frame valid > 300 ms -> semua motor berhenti */
    if (!stopped && (HAL_GetTick() - last_rx_ms) > FAILSAFE_MS) robot_stop();
}

void HAL_UART_RxCpltCallback(UART_HandleTypeDef *h)
{
    if (h->Instance == USART1) {
        char ch = (char)rx_byte;
        if (ch == '\n' || ch == '\r') {
            if (idx > 0) {
                line[idx] = 0;
                memcpy(ready, line, sizeof ready);
                line_ready = 1;
            }
            idx = 0;
        } else if (idx < sizeof(line) - 1) {
            line[idx++] = ch;
        } else {
            idx = 0;
        }
        HAL_UART_Receive_IT(&huart1, &rx_byte, 1);
    }
}

void HAL_UART_ErrorCallback(UART_HandleTypeDef *h)
{
    if (h->Instance == USART1) HAL_UART_Receive_IT(&huart1, &rx_byte, 1);
}
