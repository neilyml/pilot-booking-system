create index idx_vessels_owner_status_created
    on vessels (owner_id, status, created_at desc, id desc);

create index idx_vessels_status_created
    on vessels (status, created_at desc, id desc);

create index idx_bookings_owner_status_created
    on bookings (requested_by, status, created_at desc, id desc);

create index idx_bookings_status_created
    on bookings (status, created_at desc, id desc);

create index idx_coupons_owner_status_created
    on coupons (owner_id, status, created_at desc, id desc);

create index idx_coupons_status_created
    on coupons (status, created_at desc, id desc);

create index idx_routes_active_created
    on routes (active, created_at desc, id desc);

create index idx_pilots_status_created
    on pilots (status, created_at desc, id desc);
