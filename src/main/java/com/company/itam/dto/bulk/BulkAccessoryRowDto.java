package com.company.itam.dto.bulk;

public class BulkAccessoryRowDto {
    private String name;
    private String category;
    private String manufacturer;
    private String modelNumber;
    private Integer totalQuantity;
    private String purchaseCost;
    private String location;
    private String notes;

    public BulkAccessoryRowDto() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getManufacturer() { return manufacturer; }
    public void setManufacturer(String manufacturer) { this.manufacturer = manufacturer; }

    public String getModelNumber() { return modelNumber; }
    public void setModelNumber(String modelNumber) { this.modelNumber = modelNumber; }

    public Integer getTotalQuantity() { return totalQuantity; }
    public void setTotalQuantity(Integer totalQuantity) { this.totalQuantity = totalQuantity; }

    public String getPurchaseCost() { return purchaseCost; }
    public void setPurchaseCost(String purchaseCost) { this.purchaseCost = purchaseCost; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
