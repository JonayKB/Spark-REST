package es.jonaykb.spark_rest;

import net.minecraftforge.common.ForgeConfigSpec;

public class ModConfig {
    public static final ForgeConfigSpec COMMON_CONFIG;

    public static final ForgeConfigSpec.IntValue PORT;
    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.ConfigValue<String> ENDPOINT;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.push("general");

        PORT = builder
                .comment("Port to run the HTTP server on")
                .defineInRange("port", 8080, 0, 65535);
        ENDPOINT = builder
                .comment("Endpoint to expose metrics")
                .define("endpoint", "metrics");

        ENABLED = builder
                .comment("Mod is enabled")
                .define("enabled", true);

        builder.pop();

        COMMON_CONFIG = builder.build();
    }
}
