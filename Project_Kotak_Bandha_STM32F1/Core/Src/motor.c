/*
 * motor.c
 *
 *  Created on: Nov 20, 2026
 *      Author: Syifa Khalidiya
 */


#include "motor.h"

extern TIM_HandleTypeDef htim2;

#define MOTOR_PWM_MAX 99

static int8_t last_direction = 0;

void Motor_Init(void)
{
    // Pastikan kedua PWM bernilai nol sebelum dimulai
    __HAL_TIM_SET_COMPARE(&htim2, TIM_CHANNEL_1, 0);
    __HAL_TIM_SET_COMPARE(&htim2, TIM_CHANNEL_2, 0);

    HAL_TIM_PWM_Start(&htim2, TIM_CHANNEL_1);
    HAL_TIM_PWM_Start(&htim2, TIM_CHANNEL_2);

    last_direction = 0;
}

void Motor_SetSignedPWM(int16_t pwm)
{
    int16_t duty;

    if (pwm > MOTOR_PWM_MAX)
        pwm = MOTOR_PWM_MAX;

    if (pwm < -MOTOR_PWM_MAX)
        pwm = -MOTOR_PWM_MAX;

    // PWM nol = berhenti
    if (pwm == 0)
    {
        __HAL_TIM_SET_COMPARE(&htim2, TIM_CHANNEL_1, 0);
        __HAL_TIM_SET_COMPARE(&htim2, TIM_CHANNEL_2, 0);

        last_direction = 0;
        return;
    }

    int8_t direction = (pwm > 0) ? 1 : -1;

    // Beri satu periode kontrol dengan kedua PWM nol
    // ketika arah putaran berubah.
    if ((last_direction != 0) &&
        (direction != last_direction))
    {
        __HAL_TIM_SET_COMPARE(&htim2, TIM_CHANNEL_1, 0);
        __HAL_TIM_SET_COMPARE(&htim2, TIM_CHANNEL_2, 0);

        last_direction = direction;
        return;
    }

    duty = (pwm > 0) ? pwm : -pwm;

    if (direction > 0)
    {
        __HAL_TIM_SET_COMPARE(
            &htim2,
            TIM_CHANNEL_1,
            duty
        );

        __HAL_TIM_SET_COMPARE(
            &htim2,
            TIM_CHANNEL_2,
            0
        );
    }
    else
    {
        __HAL_TIM_SET_COMPARE(
            &htim2,
            TIM_CHANNEL_1,
            0
        );

        __HAL_TIM_SET_COMPARE(
            &htim2,
            TIM_CHANNEL_2,
            duty
        );
    }

    last_direction = direction;
}

void Motor_Stop(void)
{
    __HAL_TIM_SET_COMPARE(&htim2, TIM_CHANNEL_1, 0);
    __HAL_TIM_SET_COMPARE(&htim2, TIM_CHANNEL_2, 0);

    last_direction = 0;
}


