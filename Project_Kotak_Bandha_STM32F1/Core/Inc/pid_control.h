/*
 * pid_control.h
 *
 *  Created on: Nov 20, 2026
 *      Author: Syifa Khalidiya
 */

#ifndef INC_PID_CONTROL_H_
#define INC_PID_CONTROL_H_

#include "main.h"
#include <stdint.h>

void PIDControl_Init(void);
void PIDControl_SetTarget(int32_t target);
void PIDControl_Update(void);
void PIDControl_Stop(void);

uint8_t PIDControl_IsActive(void);
int32_t PIDControl_GetTarget(void);

#endif /* INC_PID_CONTROL_H_ */
