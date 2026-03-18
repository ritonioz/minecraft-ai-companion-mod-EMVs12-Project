package AiCompanion.aicompanion2_0;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.ai.pathing.MobNavigation;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.world.EntityView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class AIEntity extends TameableEntity {
    private static final TrackedData<Boolean> TUX_MODE =
        DataTracker.registerData(AIEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private int tuxTicksRemaining = 0;

    public AIEntity(EntityType<? extends TameableEntity> entityType, World world) {
        super(entityType, world);
        this.setInvulnerable(true);
        this.setCustomName(Text.translatable("entity.aicompanion2_0.ai_companion"));
        this.setCustomNameVisible(true);
        // Ensure navigation is set up for following
        this.navigation = new MobNavigation(this, world);
    }

    public static DefaultAttributeContainer.Builder createMobAttributes() {
        return TameableEntity.createMobAttributes()
            .add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0)
            .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.3)
            .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32.0);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(TUX_MODE, false);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(1, new FollowOwnerGoal(this, 1.0, 5.0f, 2.0f, false));
        this.goalSelector.add(2, new WanderAroundFarGoal(this, 0.8));
        this.goalSelector.add(3, new LookAtEntityGoal(this, PlayerEntity.class, 8.0f));
        this.goalSelector.add(4, new LookAroundGoal(this));
    }

    @Override
    public void tick() {
        super.tick();

        if (!this.getWorld().isClient() && tuxTicksRemaining > 0) {
            tuxTicksRemaining--;

            if (tuxTicksRemaining == 0) {
                this.dataTracker.set(TUX_MODE, false);
            }
        }
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        // Allow kill command through, block everything else
        if (source == this.getDamageSources().outOfWorld()) return super.damage(source, amount);
        return false;
    }

    @Override
    public void kill() {
        super.kill();
    }

    @Override
    public Text getName() {
        return Text.translatable("entity.aicompanion2_0.ai_companion");
    }

    @Override
    public Text getDisplayName() {
        return Text.translatable("entity.aicompanion2_0.ai_companion");
    }

    @Override
    public boolean isBreedingItem(ItemStack stack) {
        return false;
    }

    @Nullable
    @Override
    public PassiveEntity createChild(ServerWorld world, PassiveEntity entity) {
        return null;
    }

    public void activateTuxMode(int durationTicks) {
        tuxTicksRemaining = Math.max(tuxTicksRemaining, durationTicks);
        this.dataTracker.set(TUX_MODE, true);
    }

    public boolean isTuxMode() {
        return this.dataTracker.get(TUX_MODE);
    }

    // Bridge method fix
    public EntityView method_48926() {
        return (EntityView) this.getWorld();
    }
}
