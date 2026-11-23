/*
 * encoder.h
 *
 *  Created on: Nov 20, 2026
 *      Author: Syifa Khalidiya
 */

#ifndef INC_ENCODER_H_
#define INC_ENCODER_H_

#include "main.h"
#include <stdint.h>

void Encoder_Init(void);
void Encoder_Update(void);
int32_t Encoder_GetPosition(void);
void Encoder_ResetPosition(void);


#endif /* INC_ENCODER_H_ */
