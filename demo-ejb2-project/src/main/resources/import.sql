create table Registrant (id bigint not null, email varchar(255) not null, name varchar(25) not null, phone_number varchar(12) not null, primary key (id));
insert into Registrant(id, name, email, phone_number) values (0, 'John Smith', 'john.smith@mailinator.com', '2125551212') 
