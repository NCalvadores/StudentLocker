package com.ifelse101.studentlocker.fragments.home;

import java.util.Objects;

public class CategoryModel {
    private final String name;
    private final int documentCount;

    public CategoryModel(String name, int documentCount) {
        this.name = name;
        this.documentCount = documentCount;
    }

    public String getName() {
        return name;
    }

    public int getDocumentCount() {
        return documentCount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CategoryModel that = (CategoryModel) o;
        return documentCount == that.documentCount && Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, documentCount);
    }
}
