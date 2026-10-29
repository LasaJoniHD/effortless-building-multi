package nl.requios.effortlessbuilding.backend;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

/** Stops shape and modifier expansion before allocating unbounded position collections. */
public final class BuildBudget {
    public static final int MAX_GENERATED_POSITIONS = 200_000;
    private static final class Counter { int work; }
    private static final ThreadLocal<Counter> ACTIVE = new ThreadLocal<>();
    private BuildBudget() {}
    public static void begin() { ACTIVE.set(new Counter()); }
    public static void end() { ACTIVE.remove(); }
    public static void visit() {
        Counter counter = ACTIVE.get();
        if (counter != null && ++counter.work > 1_000_000) throw new Exceeded();
    }
    /** Bound scanning work too: hollow shapes can visit many cells without adding entries. */
    public static boolean acceptsBounds(BlockPos first, BlockPos second, BlockPos third) {
        BlockPos end = third == null ? second : third;
        long x = Math.max(Math.abs((long) first.getX() - second.getX()), Math.abs((long) first.getX() - end.getX())) + 1;
        long y = Math.max(Math.abs((long) first.getY() - second.getY()), Math.abs((long) first.getY() - end.getY())) + 1;
        long z = Math.max(Math.abs((long) first.getZ() - second.getZ()), Math.abs((long) first.getZ() - end.getZ())) + 1;
        return x <= 1000 && y <= 1000 && z <= 1000 && x * y * z <= 1_000_000;
    }
    public static void check(int size) {
        if (ACTIVE.get() != null && size >= MAX_GENERATED_POSITIONS) throw new Exceeded();
    }
    public static List<BlockPos> positions() {
        if (ACTIVE.get() == null) return new ArrayList<>();
        return new ArrayList<>() {
            @Override
            public boolean add(BlockPos pos) {
                visit();
                check(size());
                return super.add(pos);
            }
        };
    }
    public static final class Exceeded extends RuntimeException {
        private Exceeded() { super("Build exceeds generation budget", null, false, false); }
    }
}
