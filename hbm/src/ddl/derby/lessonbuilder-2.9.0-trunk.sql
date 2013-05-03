alter table lesson_builder_items add showPeerEval smallint;
alter table lesson_builder_items add groupOwned smallint;
alter table lesson_builder_items add ownerGroups varchar(4000);
alter table lesson_builder_items add attributeString clob(255);
alter table lesson_builder_pages add groupid varchar(36);
    create table lesson_builder_peer_eval_results (
        PEER_EVAL_RESULT_ID bigint not null,
        PAGE_ID bigint not null,
        TIME_POSTED timestamp,
        GRADER varchar(255) not null,
        GRADEE varchar(255) not null,
        ROW_TEXT varchar(255) not null,
        COLUMN_VALUE integer not null,
        SELECTED smallint,
        primary key (PEER_EVAL_RESULT_ID)
    );

    create table lesson_builder_q_responses (
        id bigint not null,
        timeAnswered timestamp not null,
        questionId bigint not null,
        userId varchar(255) not null,
        correct smallint not null,
        shortanswer clob(255),
        multipleChoiceId bigint,
        originalText clob(255),
        overridden smallint not null,
        points double,
        primary key (id)
    );

    create table lesson_builder_qr_totals (
        id bigint not null,
        questionId bigint,
        responseId bigint,
        respcount bigint,
        primary key (id)
    );

alter table lesson_builder_student_pages add groupid varchar(36);
