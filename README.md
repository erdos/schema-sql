# schema-sql

_Annotate your data with Schema and generate an SQL view of it._

This library will help you to insert JSON documents into an SQL database. You need to annotate your data with schema and the sql scripts are generated automatically.

## Usage

First, create data description using the great Prismatic Schema library.

``` clojure
(defschema Partner {:name sc/Str
                    :id sc/Num
                    :role sc/Str})

(defschema DateInterval {:startDate java.util.Date,
                         :endDate (sc/maybe java.util.Date)})

(defschema Policy {:term DateInterval
                   :ref sc/Str
                   :partners [Partner]
                   :active sc/Bool})

```

Then you can generate SQL DDL to create the tables for your data.

``` clojure
(-> Policy schema->sql println)
```

Output:

``` sql
CREATE TABLE policy(
	id BIGINT NOT NULL,
	ref VARCHAR NOT NULL,
	active BIT NOT NULL
);
CREATE TABLE date_interval(
	policy_id BIGINT NOT NULL,
	start_date DATE NOT NULL,
	end_date DATE
);
CREATE TABLE partner(
	policy_id BIGINT NOT NULL,
	name VARCHAR NOT NULL,
	role VARCHAR NOT NULL
);
```

Next step is to generate insert statements.

## Status

This is an experimental project. Goals are:
- DDL to for table creation
- DML to create insert statements for data

## License

Copyright © 2017 Janos Erdos

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
