package com.aaltay.musicnotifications.mixin;

import com.aaltay.musicnotifications.ConfigManager;
import com.aaltay.musicnotifications.MediaToast;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundManager.class)
public abstract class SoundManagerMixin {

    @Inject(method = "play", at = @At("HEAD"), cancellable = true)
    private void musicnotifications_cancelToastSound(SoundInstance sound, CallbackInfoReturnable<Object> cir) {
        if (sound != null) {
            Identifier soundId = sound.getIdentifier();
            String path = soundId == null ? "" : soundId.getPath();
            if (path.equals("ui.toast.in") || path.equals("ui.toast.out") || path.equals("ui.toast.challenge_complete")) {
                long duration = (long) (ConfigManager.config.durationSeconds) * 1000L;
                long elapsed = System.currentTimeMillis() - MediaToast.lastShownTime;
                
                if (elapsed < (duration + 1500)) {
                    cir.setReturnValue(null);
                }
            }
        }
    }
}
