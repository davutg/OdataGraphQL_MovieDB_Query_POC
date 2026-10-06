package dev.poc;
final class TestLimits {
 static Limits defaults(){return new Limits(100,500,50,10000,2,2000,5000,3000,60,8,new Limits.IndexPolicy("OFF",true,10000,100000));}
}
