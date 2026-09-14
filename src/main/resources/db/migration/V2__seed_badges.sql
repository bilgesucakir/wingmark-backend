insert into badges (id, created_at, updated_at, name, description, icon, criteria_type, criteria_value, criteria_metadata, tier)
values
    ('11111111-1111-1111-1111-111111111101', now(), now(), 'First Sighting', 'Log your first bird', 'egg', 'TOTAL_LOGS', 1, null, 'BRONZE'),
    ('11111111-1111-1111-1111-111111111102', now(), now(), 'Getting Started', 'Log 10 birds', 'binoculars', 'TOTAL_LOGS', 10, null, 'SILVER'),
    ('11111111-1111-1111-1111-111111111103', now(), now(), 'Dedicated Birder', 'Log 100 birds', 'trophy', 'TOTAL_LOGS', 100, null, 'GOLD'),

    ('11111111-1111-1111-1111-111111111201', now(), now(), 'Species Spotter', 'Log 5 different species', 'feather', 'UNIQUE_SPECIES', 5, null, 'BRONZE'),
    ('11111111-1111-1111-1111-111111111202', now(), now(), 'Species Collector', 'Log 25 different species', 'feather', 'UNIQUE_SPECIES', 25, null, 'SILVER'),
    ('11111111-1111-1111-1111-111111111203', now(), now(), 'Master Birder', 'Log 75 different species', 'feather', 'UNIQUE_SPECIES', 75, null, 'GOLD'),

    ('11111111-1111-1111-1111-111111111301', now(), now(), 'Nest Watcher', 'Log 5 baby birds', 'chick', 'BABY_LOGS', 5, null, 'BRONZE'),

    ('11111111-1111-1111-1111-111111111401', now(), now(), 'Mystery Bird', 'Log 5 birds you could not identify', 'question-mark', 'UNKNOWN_SPECIES_LOGS', 5, null, 'BRONZE'),

    ('11111111-1111-1111-1111-111111111501', now(), now(), 'Proud Pet Parent', 'Log your pet for the first time', 'paw', 'PET_LOGS', 1, null, 'BRONZE'),

    ('11111111-1111-1111-1111-111111111601', now(), now(), 'Local Explorer', 'Spot 10 different species within 5km of one spot', 'map-pin', 'SPECIES_IN_RADIUS', 10, '{"radiusMeters": 5000}', 'SILVER');
