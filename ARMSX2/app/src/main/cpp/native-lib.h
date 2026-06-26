//
// Created by tyler on 3/29/2026.
//

#ifndef PASX2_2_NATIVE_LIB_H
#define PASX2_2_NATIVE_LIB_H

#endif //PASX2_2_NATIVE_LIB_H

namespace Native {
    void vmSetPaused(bool select_directory);
    // PS2 pad rumble -> active Android gamepad vibrator. Motors are 0..255.
    void onPadRumble(int largeMotor, int smallMotor);
}
