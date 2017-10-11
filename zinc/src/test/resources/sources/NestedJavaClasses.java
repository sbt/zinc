class NestedJavaClasses {
    public NestedJavaClasses nest() {
        return new NestedJavaClasses() {
            @Override
            public NestedJavaClasses nest() {
                return new NestedJavaClasses() {
                    @Override
                    public NestedJavaClasses nest() {
                        throw new RuntimeException("Way too deep!");
                    }
                };
            }
        };
    }
}