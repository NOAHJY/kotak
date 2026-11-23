#include "pid_control.h"
#include "encoder.h"
#include "motor.h"
#include "PID.h"
#include <stdlib.h>

// PID sampling = 10 ms (100 Hz)
#define PID_SAMPLE_TIME 0.01f

// Toleransi posisi awal, dalam counts
#define POSITION_TOLERANCE 3

// Batas output mengikuti ARR = 99
#define PID_OUTPUT_MAX 99.0f

// Nilai gain belum dituning
#define PID_KP 0.0f
#define PID_KI 0.0f
#define PID_KD 0.0f

static PIDController pid;

static int32_t target_position = 0;
static uint8_t pid_active = 0;

void PIDControl_Init(void)
{
    pid.Kp = PID_KP;
    pid.Ki = PID_KI;
    pid.Kd = PID_KD;

    pid.tau = 0.02f;

    pid.T = PID_SAMPLE_TIME;

    pid.limMin = -PID_OUTPUT_MAX;
    pid.limMax = PID_OUTPUT_MAX;

    pid.limMinInt = -50.0f;
    pid.limMaxInt = 50.0f;

    PIDController_Init(&pid);

    target_position = 0;
    pid_active = 0;

    Motor_Stop();
}

void PIDControl_SetTarget(int32_t target)
{
    target_position = target;

    // Reset memori PID sebelum target baru
    PIDController_Init(&pid);

    pid_active = 1;
}

void PIDControl_Update(void)
{
    if (!pid_active)
        return;

    int32_t current_position = Encoder_GetPosition();

    int32_t error =
        target_position - current_position;

    // Jika target sudah tercapai
    if (abs(error) <= POSITION_TOLERANCE)
    {
        Motor_Stop();
        pid_active = 0;
        return;
    }

    float output = PIDController_Update(
        &pid,
        (float)target_position,
        (float)current_position
    );

    Motor_SetSignedPWM((int16_t)output);
}

void PIDControl_Stop(void)
{
    pid_active = 0;

    Motor_Stop();

    PIDController_Init(&pid);
}

uint8_t PIDControl_IsActive(void)
{
    return pid_active;
}

int32_t PIDControl_GetTarget(void)
{
    return target_position;
}

/*
 * pid_control.c
 *
 *  Created on: Nov 20, 2026
 *      Author: Syifa Khalidiya
 */


