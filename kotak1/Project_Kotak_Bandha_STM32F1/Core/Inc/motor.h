/*
 * motor.h
 *
 *  Created on: Nov 20, 2026
 *      Author: Syifa Khalidiya
 */

#ifndef INC_MOTOR_H_
#define INC_MOTOR_H_

#include "main.h"
#include <stdint.h>

void Motor_Init(void);
void Motor_SetSignedPWM(int16_t pwm);
void Motor_Stop(void);

#endif /* INC_MOTOR_H_ */
