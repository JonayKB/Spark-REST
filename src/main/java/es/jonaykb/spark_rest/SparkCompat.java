package es.jonaykb.spark_rest;
import me.lucko.spark.api.Spark;
import me.lucko.spark.api.SparkProvider;

public class SparkCompat {

    public static Spark tryLoad() {
        try {
            return SparkProvider.get();
        } catch (Throwable t) {
            return null;
        }
    }
}