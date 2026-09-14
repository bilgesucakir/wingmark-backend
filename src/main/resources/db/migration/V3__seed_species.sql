insert into species (id, created_at, updated_at, common_name, scientific_name, family, taxonomic_order, description, lifespan, diet, habitat, size_description, conservation_status, native_range)
values
    ('22222222-2222-2222-2222-222222222201', now(), now(), 'House Sparrow', 'Passer domesticus', 'Passeridae', 'Passeriformes',
     'A small, stocky bird with a thick bill for eating seeds. Males have a grey crown and black bib; females are plain brown and buff.',
     '3-5 years', 'Seeds, grains, and insects (especially when feeding young)', 'Urban and suburban areas, farmland, close to human settlement',
     '14-18cm, wingspan 19-25cm', 'Least Concern', 'Native to Europe, Asia and North Africa; introduced worldwide'),

    ('22222222-2222-2222-2222-222222222202', now(), now(), 'European Robin', 'Erithacus rubecula', 'Muscicapidae', 'Passeriformes',
     'Small insectivorous bird best known for its bright orange-red breast and face.',
     '2 years (up to 8-11 in the wild)', 'Insects, worms, seeds and berries', 'Gardens, woodlands, hedgerows and parks',
     '12.5-14cm, wingspan 20-22cm', 'Least Concern', 'Europe, western Siberia and parts of North Africa'),

    ('22222222-2222-2222-2222-222222222203', now(), now(), 'Mallard', 'Anas platyrhynchos', 'Anatidae', 'Anseriformes',
     'The most common and widespread dabbling duck. Males have an iridescent green head and yellow bill; females are mottled brown.',
     '5-10 years', 'Aquatic plants, seeds, insects, small fish', 'Lakes, ponds, rivers, wetlands and urban parks',
     '50-65cm, wingspan 81-98cm', 'Least Concern', 'Native across the Northern Hemisphere');

insert into species_images (id, created_at, updated_at, species_id, life_stage, gender, image_url, caption)
values
    ('33333333-3333-3333-3333-333333333301', now(), now(), '22222222-2222-2222-2222-222222222201', 'ADULT', 'MALE', 'https://upload.wikimedia.org/wikipedia/commons/4/48/Passer_domesticus_adult%2Cwinter-male.jpg', 'Adult male in winter plumage, grey crown, chestnut nape, black bib'),
    ('33333333-3333-3333-3333-333333333302', now(), now(), '22222222-2222-2222-2222-222222222201', 'ADULT', 'FEMALE', 'https://upload.wikimedia.org/wikipedia/commons/b/b3/Female_house_sparrow_at_Kodai.jpg', 'Adult female, plain brown and buff'),
    ('33333333-3333-3333-3333-333333333303', now(), now(), '22222222-2222-2222-2222-222222222202', 'ADULT', 'NOT_APPLICABLE', 'https://upload.wikimedia.org/wikipedia/commons/f/f3/Erithacus_rubecula_with_cocked_head.jpg', 'Adult, sexes look alike');
