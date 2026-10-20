package com.toolminer;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Two features:
 *  1. Right click with a pickaxe toggles continuous mining on. Right click again turns it off.
 *  2. Auto tool: while mining, switches to the fastest hotbar tool for the block you look at,
 *     and goes back to your old slot when you stop.
 */
public class ToolMinerClient implements ClientModInitializer {
    private static boolean mining = false;
    private static boolean lastUse = false;
    private static int previousSlot = -1;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.START_CLIENT_TICK.register(ToolMinerClient::tick);
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            mining = false;
            lastUse = false;
            previousSlot = -1;
            return;
        }

        // Any open screen (inventory, chest, chat...) stops mining
        if (mc.screen != null) {
            stopMining(mc);
            lastUse = mc.options.keyUse.isDown();
            return;
        }

        boolean use = mc.options.keyUse.isDown();
        boolean justPressed = use && !lastUse;
        lastUse = use;

        if (justPressed) {
            if (mining) {
                stopMining(mc);
                eatUseClicks(mc);
            } else if (mc.player.getMainHandItem().is(ItemTags.PICKAXES)) {
                mining = true;
                eatUseClicks(mc);
            }
        }

        if (mining) mc.options.keyAttack.setDown(true);

        autoTool(mc);
    }

    // stops the right click from also opening chests, placing things, etc.
    private static void eatUseClicks(Minecraft mc) {
        while (mc.options.keyUse.consumeClick()) { }
    }

    private static void stopMining(Minecraft mc) {
        if (mining) mc.options.keyAttack.setDown(false);
        mining = false;
    }

    private static void autoTool(Minecraft mc) {
        boolean breaking = mc.options.keyAttack.isDown()
            && mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK;

        if (!breaking) {
            if (previousSlot != -1) {
                mc.player.getInventory().setSelectedSlot(previousSlot);
                previousSlot = -1;
            }
            return;
        }

        BlockState state = mc.level.getBlockState(((BlockHitResult) mc.hitResult).getBlockPos());
        if (state.isAir()) return;

        int current = mc.player.getInventory().getSelectedSlot();
        int best = current;
        float bestSpeed = speed(mc.player.getInventory().getItem(current), state);

        for (int i = 0; i < 9; i++) {
            float s = speed(mc.player.getInventory().getItem(i), state);
            if (s > bestSpeed) {
                bestSpeed = s;
                best = i;
            }
        }

        if (best != current) {
            if (previousSlot == -1) previousSlot = current;
            mc.player.getInventory().setSelectedSlot(best);
        }
    }

    private static float speed(ItemStack stack, BlockState state) {
        return stack.getDestroySpeed(state);
    }
}
