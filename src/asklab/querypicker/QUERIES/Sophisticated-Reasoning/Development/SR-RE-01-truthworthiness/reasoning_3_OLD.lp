%%%%
% This file is a reasoning for satisfaction of property based on property requirements supporting it
%%%%

step(0).

concern(authorization).

property(trusted_auth_device).
property(trusted_environment).
property(auth_strategies).

obs(trusted_auth_device,true).
obs(trusted_environment,true).
obs(auth_strategies,unknown).

req(finger_printing).
req(two_factors).
req(iris_scan).
req(oauth).
req(opt_code).
req(email_verify).
req(ip_check).

obs(finger_printing,false).
obs(two_factors,false).
obs(iris_scan,false).
obs(oauth,true).
obs(opt_code,true).
obs(email_verify,false).
obs(ip_check,true).

group(1..5).

suppBy(finger_printing,auth_strategies,1).
suppBy(two_factors,auth_strategies,2).
suppBy(iris_scan,auth_strategies,3).

suppBy(oauth,auth_strategies,4).
suppBy(opt_code,auth_strategies,4).

suppBy(oauth,auth_strategies,5).
suppBy(email_verify,auth_strategies,5).
suppBy(ip_check,auth_strategies,5).

% REASONING

h(P,0) :- obs(P,true), property(P).
-h(P,0) :- obs(P,false), property(P).

%leadTo(P,G) :- property(P), obs(P,unknown), group(G), N = #count{R : suppBy(R,P,G), obs(R,true)}, M = #count{R : suppBy(R,P,G)}, M = N, M > 0.

leadTo(P,G) :- property(P), obs(P,unknown), group(G), N = #count{R : suppBy(R,P,G), not obs(R,true)}, M = #count{R : suppBy(R,P,G)}, M > 0, N = 0. 

h(P,0) :- leadTo(P,G), property(P), group(G).
-h(P,0) :- not h(P,0), property(P).

#show h/2.
#show -h/2.
#show leadTo/2.