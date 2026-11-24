
#include "encoder.h"

extern TIM_HandleTypeDef htim1;

static volatile int32_t encoder_position = 0;
static uint16_t encoder_last_count = 0;

void Encoder_Init(void)
{
    HAL_TIM_Encoder_Start(&htim1, TIM_CHANNEL_ALL);

    encoder_last_count =
        (uint16_t)__HAL_TIM_GET_COUNTER(&htim1);

    encoder_position = 0;
}

void Encoder_Update(void)
{
    uint16_t current_count =
        (uint16_t)__HAL_TIM_GET_COUNTER(&htim1);

    int32_t delta =
        (int32_t)current_count -
        (int32_t)encoder_last_count;

    // Menangani overflow dan underflow counter 16-bit
    if (delta > 32767)
    {
        delta -= 65536;
    }
    else if (delta < -32768)
    {
        delta += 65536;
    }

    encoder_position += delta;
    encoder_last_count = current_count;
}

int32_t Encoder_GetPosition(void)
{
    return encoder_position;
}

void Encoder_ResetPosition(void)
{
    __HAL_TIM_SET_COUNTER(&htim1, 0);

    encoder_last_count = 0;
    encoder_position = 0;
}


/*
 * encoder.c
 *
 *  Created on: Nov 20, 2026
 *      Author: Syifa Khalidiya
 */


