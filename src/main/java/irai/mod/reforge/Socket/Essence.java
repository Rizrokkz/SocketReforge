package irai.mod.reforge.Socket;

import java.util.List;

public class Essence {

    public enum Tier { T1, T2, T3, T4, T5 }

    public enum Type { FIRE, ICE, LIGHTNING, LIFE, VOID, WATER }

    private final String          id;
    private final Tier            tier;
    private final Type            type;
    private final List<EssenceEffect> effects;

    public Essence(String id, Tier tier, Type type, List<EssenceEffect> effects) {
        this.id      = id;
        this.tier    = tier;
        this.type    = type;
        this.effects = effects;
    }

    public String              getId()      { return id; }
    public Tier                getTier()    { return tier; }
    public Type                getType()    { return type; }
    public List<EssenceEffect> getEffects() { return effects; }

    /** Human-readable display name, e.g. "Fire Essence (T3)" */
    public String getDisplayName() {
        return type.name().charAt(0)
             + type.name().substring(1).toLowerCase()
             + " Essence (T" + (tier.ordinal() + 1) + ")";
    }
}