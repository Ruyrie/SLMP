package com.zhimu.slmp.breed.model.request;

import androidx.annotation.Nullable;

/**
 * 新增母猪（API-B07）/ 编辑档案（API-B08）。
 */
public class BreedingAnimalRequest {

    @Nullable
    private String earTag;
    @Nullable
    private Long breedId;
    @Nullable
    private String birthDate;
    @Nullable
    private String source;
    @Nullable
    private Long penId;
    @Nullable
    private Integer initialParity;
    /** B08 必填 */
    @Nullable
    private Integer version;

    public BreedingAnimalRequest setEarTag(@Nullable String earTag) {
        this.earTag = earTag;
        return this;
    }

    public BreedingAnimalRequest setBreedId(@Nullable Long breedId) {
        this.breedId = breedId;
        return this;
    }

    public BreedingAnimalRequest setBirthDate(@Nullable String birthDate) {
        this.birthDate = birthDate;
        return this;
    }

    public BreedingAnimalRequest setSource(@Nullable String source) {
        this.source = source;
        return this;
    }

    public BreedingAnimalRequest setPenId(@Nullable Long penId) {
        this.penId = penId;
        return this;
    }

    public BreedingAnimalRequest setInitialParity(@Nullable Integer initialParity) {
        this.initialParity = initialParity;
        return this;
    }

    public BreedingAnimalRequest setVersion(@Nullable Integer version) {
        this.version = version;
        return this;
    }

    @Nullable
    public String getEarTag() {
        return earTag;
    }

    @Nullable
    public Long getBreedId() {
        return breedId;
    }

    @Nullable
    public String getBirthDate() {
        return birthDate;
    }

    @Nullable
    public String getSource() {
        return source;
    }

    @Nullable
    public Long getPenId() {
        return penId;
    }

    @Nullable
    public Integer getInitialParity() {
        return initialParity;
    }

    @Nullable
    public Integer getVersion() {
        return version;
    }
}
