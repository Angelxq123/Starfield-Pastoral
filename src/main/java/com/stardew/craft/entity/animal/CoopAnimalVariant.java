package com.stardew.craft.entity.animal;

public enum CoopAnimalVariant {
	WHITE_CHICKEN(
			"geo/entity/animal/white_chicken.geo.json",
			"animations/entity/animal/white_chicken.animation.json",
			"textures/entity/animal/white_chicken.png",
			"geo/entity/animal/baby_chicken.geo.json",
			"animations/entity/animal/baby_chicken.animation.json",
			"textures/entity/animal/baby_chicken.png"
	),
	GOLDEN_CHICKEN(
			"geo/entity/animal/golden_chicken.geo.json",
			"animations/entity/animal/golden_chicken.animation.json",
			"textures/entity/animal/golden_chicken.png",
			"geo/entity/animal/baby_chicken.geo.json",
			"animations/entity/animal/baby_chicken.animation.json",
			"textures/entity/animal/baby_chicken.png"
	),
	DUCK(
			"geo/entity/animal/duck.geo.json",
			"animations/entity/animal/duck.animation.json",
			"textures/entity/animal/duck.png",
			"geo/entity/animal/baby_chicken.geo.json",
			"animations/entity/animal/baby_chicken.animation.json",
			"textures/entity/animal/baby_chicken.png"
	),
	VOID_CHICKEN(
			"geo/entity/animal/void_chicken.geo.json",
			"animations/entity/animal/void_chicken.animation.json",
			"textures/entity/animal/void_chicken.png",
			"geo/entity/animal/baby_void_chicken.geo.json",
			"animations/entity/animal/baby_void_chicken.animation.json",
			"textures/entity/animal/baby_void_chicken.png"
	),
	RABBIT(
			"geo/entity/animal/rabbit.geo.json",
			"animations/entity/animal/rabbit.animation.json",
			"textures/entity/animal/rabbit.png",
			"geo/entity/animal/rabbit_baby.geo.json",
			"animations/entity/animal/baby_rabbit.animation.json",
			"textures/entity/animal/baby_rabbit.png"
	),
	OSTRICH(
			"geo/entity/animal/ostrich.geo.json",
			"animations/entity/animal/ostrich.animation.json",
			"textures/entity/animal/ostrich.png",
			"geo/entity/animal/baby_ostrich.geo.json",
			"animations/entity/animal/baby_ostrich.animation.json",
			"textures/entity/animal/baby_ostrich.png"
	),
	DINOSAUR(
			"geo/entity/animal/dinosaur.geo.json",
			"animations/entity/animal/dinosaur.animation.json",
			"textures/entity/animal/dinosaur.png",
			"geo/entity/animal/dinosaur.geo.json",
			"animations/entity/animal/dinosaur.animation.json",
			"textures/entity/animal/dinosaur.png"
	),
	COW(
			"geo/entity/animal/cow.geo.json",
			"animations/entity/animal/cow.animation.json",
			"textures/entity/animal/cow.png",
			"geo/entity/animal/baby_cow.geo.json",
			"animations/entity/animal/baby_cow.animation.json",
			"textures/entity/animal/baby_cow.png"
	),
	GOAT(
			"geo/entity/animal/goat.geo.json",
			"animations/entity/animal/goat.animation.json",
			"textures/entity/animal/goat.png",
			"geo/entity/animal/baby_goat.geo.json",
			"animations/entity/animal/baby_goat.animation.json",
			"textures/entity/animal/baby_goat.png"
	),
	SHEEP(
			"geo/entity/animal/sheep.geo.json",
			"animations/entity/animal/sheep.animation.json",
			"textures/entity/animal/sheep.png",
			"geo/entity/animal/baby_sheep.geo.json",
			"animations/entity/animal/baby_sheep.animation.json",
			"textures/entity/animal/baby_sheep.png"
	),
	SHEARED_SHEEP(
			"geo/entity/animal/sheared_sheep.geo.json",
			"animations/entity/animal/sheared_sheep.animation.json",
			"textures/entity/animal/sheep.png",
			"geo/entity/animal/baby_sheep.geo.json",
			"animations/entity/animal/baby_sheep.animation.json",
			"textures/entity/animal/baby_sheep.png"
	),
	PIG(
			"geo/entity/animal/pig.geo.json",
			"animations/entity/animal/pig.animation.json",
			"textures/entity/animal/pig.png",
			"geo/entity/animal/baby_pig.geo.json",
			"animations/entity/animal/baby_pig.animation.json",
			"textures/entity/animal/baby_pig.png"
	);

	private final String adultModel;
	private final String adultAnimation;
	private final String adultTexture;
	private final String babyModel;
	private final String babyAnimation;
	private final String babyTexture;

	CoopAnimalVariant(String adultModel,
	                  String adultAnimation,
	                  String adultTexture,
	                  String babyModel,
	                  String babyAnimation,
	                  String babyTexture) {
		this.adultModel = adultModel;
		this.adultAnimation = adultAnimation;
		this.adultTexture = adultTexture;
		this.babyModel = babyModel;
		this.babyAnimation = babyAnimation;
		this.babyTexture = babyTexture;
	}

	public String modelPath(boolean baby) {
		return baby ? babyModel : adultModel;
	}

	public String animationPath(boolean baby) {
		return baby ? babyAnimation : adultAnimation;
	}

	public String texturePath(boolean baby) {
		return baby ? babyTexture : adultTexture;
	}
}