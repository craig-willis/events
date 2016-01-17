package edu.gslis.temporal.util;

public class FeatureHe {
    private String key;
    private double[] value;

    public FeatureHe(String key, double[] value)
    {
        this.key = key;
        this.value = value;
    }

    public String getKey()
    {
        return this.key;
    }

    public double[] getValue()
    {
        return this.value;
    }

    public void setKey(String key)
    {
        this.key = key;
    }

    public void setValue(double[] value)
    {
        this.value = value;
    }
}