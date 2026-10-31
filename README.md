# Приложение "Исследуй со мной" (Explore With Me)
___

## *Архитектура*
Приложение пользователям делиться информацией об интересных событиях и находить компанию для участия в них.
Имеется разделение на административную, приватную и общую части.
В состав входят следующие модули:

* Discovery Server - реализация паттерна Service Discovery
* Config Server - реализация паттерна Externalized Configuration
* Gateway Server - реализация паттерна API Gateway, в том числе подключен Load Balancer
* Event Service - реализация функций по управлению событиями
* Compilation Service - реализация функций по управлению подборками событий
* User Service - реализация функций по управлению данными о пользователях
* Request Service - реализация функций по управлению запросами на участие в событиях
* Comment Service - реализация функций по управлению комментариями к событиям
* Stats Service - реализация функций по уаправлению статистическими данными по запросам информации о событиях

Конфигурации всех модулей хранятся в Config Server и загружаются при регистрации в Discovery Server.
У каждого сервиса, за исключением служебных, имеется своя база данных (реализуется паттерн Database per Service).

## *Описание внутреннего API*

Взаимодействие между модулями осуществляется при помощи OpenFeign. Для эвнутренних запросов
созданы маршруты /internal.
Имеются следующие маршруты:

* GET /admin/users/internal/exists - проверка существования пользователей по id
* GET /admin/events/internal - получение списка событий
* GET /admin/events/internal/exists - проверка существования событий по id
* GET /admin/events/internal/exists/{eventId}/{userId} - проверка существования события по id события и id инициатора события
* GET /admin/events/internal/exists/{eventId} - получение события по id
* PUT /admin/events/internal/exists/{eventId} - обновление информации о количестве свободных мест для участия в событии
* GET /users/{userId}/events/{eventId} - используется в том числе для внутренних нужд для получения события по id события и id инициатора события

## *Описание внешнего API*

Внешний API можно посмотреть в редакторе Swagger по ссылкам:

* [Спецификация основного сервиса](https://raw.githubusercontent.com/yandex-praktikum/java-explore-with-me/main/ewm-main-service-spec.json)
* [Спецификация сервиса статистики](https://raw.githubusercontent.com/yandex-praktikum/java-explore-with-me/main/ewm-stats-service-spec.json)